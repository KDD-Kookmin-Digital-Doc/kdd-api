package com.kdd.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kdd.chat.dto.ai.AiChatRequest;
import com.kdd.chat.dto.sse.DoneEvent;
import com.kdd.chat.dto.sse.ErrorEvent;
import com.kdd.chat.dto.sse.FallbackEvent;
import com.kdd.chat.dto.sse.MetaEvent;
import com.kdd.chat.dto.sse.SseSourceDto;
import com.kdd.chat.dto.sse.TextEvent;
import com.kdd.chat.entity.ConfidenceLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessagePersister persister;
    private final WebClient aiServerWebClient;

    // AI 답변 생성에 수십 초가 걸릴 수 있어 일반 HTTP 타임아웃보다 길게 설정
    private static final long SSE_TIMEOUT_MS = 300_000L;
    // 클라가 끊겨도 AI 스트림은 백그라운드에서 지속하되, AI 서버가 무한 정지할 때 구독이 새는 것을 막는 상한
    private static final Duration AI_STREAM_MAX_IDLE = Duration.ofMinutes(10);

    public SseEmitter sendMessage(Long sessionId, Long userId, String content) {
        // SSE 시작 전에 모든 DB 작업을 한 트랜잭션으로 끝내 커넥션을 즉시 반환한다.
        // open-in-view=false 환경에서 lazy 로딩이 트랜잭션 안에서 안전하게 일어나도록 하면서,
        // 이후 수십 초의 SSE 스트리밍 동안 HikariCP 커넥션이 점유되지 않게 한다.
        ChatMessagePersister.PreparedChat prepared =
                persister.prepareAndSaveUserMessage(sessionId, userId, content);

        AiChatRequest request = new AiChatRequest(
                content,
                String.valueOf(sessionId),
                prepared.userContext(),
                prepared.isFirstMessage(),
                prepared.history()
        );

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        streamFromAiServer(emitter, sessionId, request);
        return emitter;
    }

    private void streamFromAiServer(SseEmitter emitter, Long sessionId, AiChatRequest request) {
        StringBuilder contentAccumulator = new StringBuilder();
        AtomicReference<ConfidenceLevel> confidenceRef = new AtomicReference<>();
        List<AiSourceRaw> capturedSources = new ArrayList<>();
        // 클라가 끊겨도 AI 스트림 구독은 유지해 답변을 끝까지 받아 DB에 저장한다.
        // 이 플래그는 "emitter로 더 보내도 되는지"만 판단하며, 누적과 최종 영속화에는 영향이 없다.
        AtomicBoolean clientConnected = new AtomicBoolean(true);
        // AI가 명세대로 done/error 중 하나로 종료했는지 추적 — 둘 다 없이 스트림이 닫히면 비정상 종료로 처리해야 한다
        AtomicBoolean terminalReceived = new AtomicBoolean(false);

        Disposable disposable = aiServerWebClient.post()
                .uri("/api/chat")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                // AI 서버가 응답을 보내다 멈춰도 구독이 영구히 살아있지 않도록 마지막 청크 기준 상한을 둔다
                .timeout(AI_STREAM_MAX_IDLE)
                // SseEmitter.send()는 블로킹 I/O라 Netty 이벤트 루프를 점유하면 안 됨 → 별도 스케줄러로 이관
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        node -> handleEvent(emitter, node, sessionId,
                                contentAccumulator, confidenceRef, capturedSources,
                                clientConnected, terminalReceived),
                        error -> handleStreamError(emitter, error, clientConnected, terminalReceived),
                        () -> handleStreamComplete(emitter, sessionId, clientConnected, terminalReceived)
                );

        // dispose()를 호출하지 않음: 클라 연결이 끊겨도 AI 스트림은 done/error까지 지속되어야 하기 때문
        emitter.onCompletion(() -> clientConnected.set(false));
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for session {} — AI 스트림은 백그라운드에서 계속 수신", sessionId);
            clientConnected.set(false);
        });
        emitter.onError(err -> {
            log.warn("SSE error for session {}: {}", sessionId, err.getMessage());
            clientConnected.set(false);
        });
    }

    private void handleEvent(SseEmitter emitter, JsonNode node, Long sessionId,
                             StringBuilder contentAccumulator,
                             AtomicReference<ConfidenceLevel> confidenceRef,
                             List<AiSourceRaw> capturedSources,
                             AtomicBoolean clientConnected,
                             AtomicBoolean terminalReceived) {
        try {
            String type = node.path("type").asText();
            switch (type) {
                case "meta" -> handleMeta(emitter, node, confidenceRef, capturedSources, clientConnected);
                case "fallback" -> handleFallback(emitter, node, contentAccumulator, clientConnected);
                case "text" -> handleText(emitter, node, contentAccumulator, clientConnected);
                case "done" -> {
                    terminalReceived.set(true);
                    handleDone(emitter, sessionId, contentAccumulator, confidenceRef, capturedSources, clientConnected);
                }
                case "error" -> {
                    terminalReceived.set(true);
                    handleAiError(emitter, node, clientConnected);
                }
                default -> log.warn("Unknown SSE event type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to process SSE event for session {}", sessionId, e);
            // handleStreamError와 동일한 패턴으로 ErrorEvent 먼저 내려주고 정상 종료해 마지막 이벤트 플러시를 보장한다
            trySend(emitter, ErrorEvent.of("답변 처리 중 오류가 발생했습니다."), clientConnected);
            if (clientConnected.get()) {
                safeComplete(emitter);
            }
        }
    }

    private void handleMeta(SseEmitter emitter, JsonNode node,
                            AtomicReference<ConfidenceLevel> confidenceRef,
                            List<AiSourceRaw> capturedSources,
                            AtomicBoolean clientConnected) {
        String subtype = node.path("subtype").asText();
        MetaEvent event = switch (subtype) {
            case "document" -> {
                String confidence = node.path("confidence").asText(null);
                List<SseSourceDto> sseSources = extractSources(node, capturedSources);
                if (confidence != null) {
                    // AI가 스펙 외 문자열을 보내도 전체 스트림이 끊어지지 않도록 파싱 실패는 무시하고 null 유지
                    try {
                        confidenceRef.set(ConfidenceLevel.from(confidence));
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown confidence level from AI server: {}", confidence);
                    }
                }
                yield MetaEvent.document(confidence, sseSources);
            }
            case "cache" -> MetaEvent.cache(extractSources(node, capturedSources));
            case "chitchat" -> MetaEvent.chitchat();
            default -> null;
        };
        if (event == null) {
            log.warn("Unknown meta subtype: {}", subtype);
            return;
        }
        trySend(emitter, event, clientConnected);
    }

    private List<SseSourceDto> extractSources(JsonNode node, List<AiSourceRaw> capturedSources) {
        List<SseSourceDto> sseSources = new ArrayList<>();
        JsonNode sourcesNode = node.path("sources");
        if (sourcesNode.isArray()) {
            for (JsonNode src : sourcesNode) {
                Long docId = parseNullableLong(src.path("doc_id").asText(null));
                Long chunkId = parseNullableLong(src.path("chunk_id").asText(null));
                String docName = src.path("doc_name").asText(null);
                Integer page = src.has("page") && !src.get("page").isNull() ? src.get("page").asInt() : null;
                sseSources.add(new SseSourceDto(docId, docName, page));
                capturedSources.add(new AiSourceRaw(docId, chunkId, docName, page));
            }
        }
        return sseSources;
    }

    private Long parseNullableLong(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void handleFallback(SseEmitter emitter, JsonNode node,
                                StringBuilder contentAccumulator,
                                AtomicBoolean clientConnected) {
        String message = node.path("message").asText("");
        List<String> suggestedQuestions = new ArrayList<>();
        JsonNode sqNode = node.path("suggested_questions");
        if (sqNode.isArray()) {
            for (JsonNode q : sqNode) {
                suggestedQuestions.add(q.asText());
            }
        }
        contentAccumulator.append(message);
        trySend(emitter, FallbackEvent.of(message, suggestedQuestions), clientConnected);
    }

    private void handleText(SseEmitter emitter, JsonNode node,
                            StringBuilder contentAccumulator,
                            AtomicBoolean clientConnected) {
        String content = node.path("content").asText("");
        contentAccumulator.append(content);
        trySend(emitter, TextEvent.of(content), clientConnected);
    }

    private void handleDone(SseEmitter emitter, Long sessionId,
                            StringBuilder contentAccumulator,
                            AtomicReference<ConfidenceLevel> confidenceRef,
                            List<AiSourceRaw> capturedSources,
                            AtomicBoolean clientConnected) {
        // 클라 연결 여부와 무관하게 DB 저장은 항상 수행 — 사용자가 돌아와서 히스토리에서 답변을 볼 수 있어야 한다
        Long messageId = persister.saveAssistantMessage(
                sessionId,
                contentAccumulator.toString(),
                confidenceRef.get(),
                capturedSources
        );
        trySend(emitter, DoneEvent.of(messageId), clientConnected);
        if (clientConnected.get()) {
            safeComplete(emitter);
        }
    }

    private void handleAiError(SseEmitter emitter, JsonNode node,
                               AtomicBoolean clientConnected) {
        String message = node.path("message").asText("답변 생성 중 오류가 발생했습니다.");
        trySend(emitter, ErrorEvent.of(message), clientConnected);
        if (clientConnected.get()) {
            safeComplete(emitter);
        }
    }

    private void handleStreamError(SseEmitter emitter, Throwable error,
                                   AtomicBoolean clientConnected,
                                   AtomicBoolean terminalReceived) {
        terminalReceived.set(true);
        log.error("AI server streaming error", error);
        trySend(emitter, ErrorEvent.of("AI 서버와의 통신에 실패했습니다."), clientConnected);
        if (clientConnected.get()) {
            // ErrorEvent를 이미 클라이언트에 내려줬으므로 completeWithError 대신 정상 종료로 마지막 이벤트 플러시 보장
            safeComplete(emitter);
        }
    }

    private void handleStreamComplete(SseEmitter emitter, Long sessionId,
                                      AtomicBoolean clientConnected,
                                      AtomicBoolean terminalReceived) {
        if (terminalReceived.get()) {
            log.debug("AI server stream closed for session {}", sessionId);
            return;
        }
        // done/error 없이 스트림이 닫히면 FE는 타임아웃까지 매달려 있고 누적본도 유실된다 — 에러로 정리
        log.warn("AI server stream closed without terminal event for session {}", sessionId);
        trySend(emitter, ErrorEvent.of("AI 서버와의 통신이 비정상 종료됐습니다."), clientConnected);
        if (clientConnected.get()) {
            safeComplete(emitter);
        }
    }

    private void trySend(SseEmitter emitter, Object event, AtomicBoolean clientConnected) {
        if (!clientConnected.get()) return;
        try {
            emitter.send(SseEmitter.event().data(event));
        } catch (Exception e) {
            // 전송 도중 클라가 끊겼을 가능성이 큼 — 플래그를 내려 이후 이벤트는 DB 누적에만 쓰이게 한다
            clientConnected.set(false);
            log.debug("Client disconnected during SSE send: {}", e.getMessage());
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

}
