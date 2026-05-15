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
import com.kdd.chat.entity.MessageCompleteness;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessagePersister persister;
    private final ChatRateLimitService chatRateLimitService;
    private final WebClient aiServerWebClient;

    // AI 답변 생성에 수십 초가 걸릴 수 있어 일반 HTTP 타임아웃보다 길게 설정
    private static final long SSE_TIMEOUT_MS = 300_000L;
    // 클라가 끊겨도 AI 스트림은 백그라운드에서 지속하되, AI 서버가 무한 정지할 때 구독이 새는 것을 막는 상한
    private static final Duration AI_STREAM_MAX_IDLE = Duration.ofMinutes(10);

    // 동일 세션에 대한 동시 송신을 차단하기 위한 in-flight 가드.
    // 두 트랜잭션이 모두 빈 히스토리를 보고 isFirstMessage=true로 판정해 USER/ASSISTANT 메시지가
    // 중복으로 영속화되고 AI 스트림이 두 개 열리는 race를 막는다. 해제는 streamFromAiServer의
    // doFinally(스트림 종료 시점)가 책임지므로 클라가 끊겨도 AI 작업이 끝날 때까지 가드가 유지된다.
    // 단일 Lightsail 배포 가정 — 수평 확장 시 advisory lock 등 분산 락으로 교체 필요.
    private final Set<Long> inFlightSessions = ConcurrentHashMap.newKeySet();

    /**
     * SSE 스트리밍 동안 모든 핸들러가 공유하는 컨텍스트. 핸들러 시그니처가 8~9개 파라미터로 부풀어지지 않도록 묶는다.
     * {@code content}/{@code confidence}/{@code sources}는 onNext에서 계속 누적되고,
     * {@code clientConnected}/{@code terminalReceived}는 lifecycle 상태 플래그.
     */
    private record StreamCtx(
            SseEmitter emitter,
            Long sessionId,
            Long userId,
            StringBuilder content,
            AtomicReference<ConfidenceLevel> confidence,
            List<AiSourceRaw> sources,
            AtomicBoolean clientConnected,
            AtomicBoolean terminalReceived,
            ChatRateLimitService.RateLimitCheckResult rateLimit
    ) {}

    public SseEmitter sendMessage(Long sessionId, Long userId, String content) {
        // 동일 세션이 이미 AI 스트림을 진행 중이면 두 번째 호출은 rate limit 차감 전에 즉시 거절.
        // 사용자가 부당하게 한도 1회를 소모하지 않도록 검증/차감보다 먼저 막는다.
        if (!tryAcquireSession(sessionId)) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_BUSY);
        }

        // streamFromAiServer까지 도달해야 doFinally가 가드 해제를 인수받는다.
        // 그 전에 예외로 빠지면 in-flight가 영구히 남으므로 finally에서 직접 해제.
        boolean handedOffToStream = false;
        try {
            // 한도 체크 + 카운트 +1. 한도 초과면 RateLimitExceededException이 위로 전파되어
            // 글로벌 핸들러가 429를 응답한다(SSE 응답이 아직 시작되지 않은 시점이므로 JSON 본문으로 나감).
            // SSE 시작 후의 카운트 처리는 race window가 생기므로 진입부에서 짧은 REQUIRES_NEW 트랜잭션으로 끝낸다.
            // 차감이 일어난 usageDate는 보존해서 보상 호출(decrement)에 같은 row를 정확히 타깃하도록 한다 —
            // 자정 경계에서 SSE가 길어져 새로 LocalDate.now()를 계산하면 다른 row를 보게 되어 보상이 실패한다.
            ChatRateLimitService.RateLimitCheckResult rateLimit = chatRateLimitService.checkAndIncrement(userId);

            ChatMessagePersister.PreparedChat prepared;
            try {
                // SSE 시작 전에 모든 DB 작업을 한 트랜잭션으로 끝내 커넥션을 즉시 반환한다.
                // open-in-view=false 환경에서 lazy 로딩이 트랜잭션 안에서 안전하게 일어나도록 하면서,
                // 이후 수십 초의 SSE 스트리밍 동안 HikariCP 커넥션이 점유되지 않게 한다.
                prepared = persister.prepareAndSaveUserMessage(sessionId, userId, content);
            } catch (RuntimeException e) {
                // 세션 검증 실패(SESSION_NOT_FOUND/SESSION_FORBIDDEN) 등으로 메시지가 저장되지 못한 경우,
                // 위에서 차감된 사용량을 되돌려 사용자가 한도 1회를 부당하게 잃지 않도록 한다.
                safelyDecrement(userId, rateLimit.usageDate());
                throw e;
            }

            AiChatRequest request = new AiChatRequest(
                    content,
                    String.valueOf(sessionId),
                    prepared.userContext(),
                    prepared.isFirstMessage(),
                    prepared.history()
            );

            SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
            streamFromAiServer(emitter, sessionId, userId, request, rateLimit);
            handedOffToStream = true;
            return emitter;
        } finally {
            if (!handedOffToStream) {
                releaseSession(sessionId);
            }
        }
    }

    private boolean tryAcquireSession(Long sessionId) {
        return inFlightSessions.add(sessionId);
    }

    // doFinally + sendMessage finally 양쪽에서 호출될 수 있는데 Set.remove는 idempotent라 두 번 불러도 안전하다.
    private void releaseSession(Long sessionId) {
        inFlightSessions.remove(sessionId);
    }

    /**
     * DB 보상 호출(decrement·partial save) 실패가 원인 예외 컨텍스트(ErrorEvent 전송 등)를 가리지 않도록
     * RuntimeException을 swallow + log로 정리하는 공통 헬퍼.
     */
    private static void swallow(Runnable action, String contextMsg) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.error(contextMsg, ex);
        }
    }

    /**
     * AI 실패 경로(handleStreamError/handleEvent catch/handleStreamComplete terminal=false)와
     * 세션 검증 실패 catch에서 호출되어 사용자가 한도 1회를 부당하게 잃지 않도록 +1된 카운트를 되돌린다.
     */
    private void safelyDecrement(Long userId, LocalDate usageDate) {
        swallow(() -> chatRateLimitService.decrement(userId, usageDate),
                "Failed to rollback chat usage decrement for userId=" + userId + ", usageDate=" + usageDate);
    }

    /**
     * AI 스트림이 done 없이 끊긴 모든 경로(통신 실패/비정상 종료/이벤트 처리 예외)에서 동일하게 수행할
     * 마무리 작업: terminal 플래그 점유 → 부분 답변 영속화 → rate-limit 차감 보상 → ErrorEvent 송신 → safeComplete.
     * 호출자는 자신만의 로깅(원인 정보)만 따로 찍으면 된다.
     * <p>
     * 내부 terminal 가드는 사실상 handleEvent catch 경로 전용 — handleDone이 terminal=true로 잡은 뒤
     * saveAssistantMessage 도중 throw해 catch로 떨어진 케이스에서 partial-save·차감 보상이 두 번 수행되는 것을 막는다.
     * handleStreamError/handleStreamComplete는 이 메서드 호출 전 이미 pre-gate로 terminal 여부를 거른다.
     */
    private void finalizeUnexpectedFailure(StreamCtx ctx, String userMessage) {
        if (!ctx.terminalReceived().get()) {
            ctx.terminalReceived().set(true);
            savePartialAssistantIfAny(ctx);
            safelyDecrement(ctx.userId(), ctx.rateLimit().usageDate());
        }
        // ErrorEvent를 이미 클라이언트에 내려주고 completeWithError 대신 정상 종료로 마지막 이벤트 플러시 보장
        trySend(ctx.emitter(), ErrorEvent.of(userMessage), ctx.clientConnected());
        if (ctx.clientConnected().get()) {
            safeComplete(ctx.emitter());
        }
    }

    /**
     * done 이벤트 없이 AI 스트림이 끊긴 경우(통신 실패/비정상 종료/이벤트 처리 예외)에도, 누적된
     * 부분 답변이 있으면 {@code partial=true}로 저장한다. 사용자가 브라우저에서 본 텍스트가 세션 재조회 시
     * 사라져 화면-히스토리 불일치가 생기는 것을 막는다. FE는 {@code partial} 플래그로 다르게 렌더링할 수 있다.
     */
    private void savePartialAssistantIfAny(StreamCtx ctx) {
        if (ctx.content().length() == 0) return;
        swallow(() -> persister.saveAssistantMessage(
                        ctx.sessionId(), ctx.content().toString(),
                        ctx.confidence().get(), ctx.sources(), MessageCompleteness.PARTIAL),
                "Failed to persist partial assistant message for session " + ctx.sessionId());
    }

    private void streamFromAiServer(SseEmitter emitter, Long sessionId, Long userId,
                                    AiChatRequest request,
                                    ChatRateLimitService.RateLimitCheckResult rateLimit) {
        StreamCtx ctx = new StreamCtx(
                emitter, sessionId, userId,
                new StringBuilder(),
                new AtomicReference<>(),
                new ArrayList<>(),
                // 클라가 끊겨도 AI 스트림 구독은 유지해 답변을 끝까지 받아 DB에 저장한다.
                // 이 플래그는 "emitter로 더 보내도 되는지"만 판단하며, 누적과 최종 영속화에는 영향이 없다.
                new AtomicBoolean(true),
                // AI가 명세대로 done/error 중 하나로 종료했는지 추적 — 둘 다 없이 스트림이 닫히면 비정상 종료로 처리해야 한다
                new AtomicBoolean(false),
                rateLimit
        );

        // 빠른 종료(캐시 히트로 즉시 done) 또는 emitter 직후 클라 단절 케이스에서 콜백 미등록 상태로
        // lifecycle 이벤트가 발생하지 않도록 subscribe() 이전에 등록해 둔다.
        // dispose()를 호출하지 않음: 클라 연결이 끊겨도 AI 스트림은 done/error까지 지속되어야 하기 때문
        emitter.onCompletion(() -> ctx.clientConnected().set(false));
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for session {} — AI 스트림은 백그라운드에서 계속 수신", sessionId);
            ctx.clientConnected().set(false);
        });
        emitter.onError(err -> {
            log.warn("SSE error for session {}: {}", sessionId, err.getMessage());
            ctx.clientConnected().set(false);
        });

        aiServerWebClient.post()
                .uri("/api/chat")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                // AI 서버가 응답을 보내다 멈춰도 구독이 영구히 살아있지 않도록 마지막 청크 기준 상한을 둔다
                .timeout(AI_STREAM_MAX_IDLE)
                // SseEmitter.send()는 블로킹 I/O라 Netty 이벤트 루프를 점유하면 안 됨 → 별도 스케줄러로 이관
                .publishOn(Schedulers.boundedElastic())
                // AI 스트림이 어떻게 종료되든(done/error 수신, 통신 실패, idle timeout) 정확히 한 번 in-flight 해제.
                // 클라가 먼저 끊겨도 AI 작업이 끝날 때까지 가드를 유지해, 재전송이 진행 중 작업과 race 나는 것을 막는다.
                .doFinally(signal -> releaseSession(sessionId))
                .subscribe(
                        node -> handleEvent(ctx, node),
                        error -> handleStreamError(ctx, error),
                        () -> handleStreamComplete(ctx)
                );
    }

    private void handleEvent(StreamCtx ctx, JsonNode node) {
        // 이미 어느 경로로든 terminal 처리됐다면(handleDone/handleAiError/이전 onNext catch) 이후 이벤트는 모두 no-op.
        // AI 서버가 done 이후에도 이벤트를 더 흘리거나, catch 처리 후 후속 이벤트가 도착해 중복 영속화가 일어나는 것을 막는다.
        if (ctx.terminalReceived().get()) return;
        try {
            String type = node.path("type").asText();
            switch (type) {
                case "meta" -> handleMeta(ctx, node);
                case "fallback" -> handleFallback(ctx, node);
                case "text" -> handleText(ctx, node);
                case "done" -> {
                    ctx.terminalReceived().set(true);
                    handleDone(ctx);
                }
                case "error" -> {
                    ctx.terminalReceived().set(true);
                    // AI가 명시적 error 이벤트를 보낸 케이스 — 답변 생성을 시도했으나 실패. 사용자는 답을 못 받음 → 차감 보상.
                    safelyDecrement(ctx.userId(), ctx.rateLimit().usageDate());
                    handleAiError(ctx, node);
                }
                default -> log.warn("Unknown SSE event type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to process SSE event for session {}", ctx.sessionId(), e);
            finalizeUnexpectedFailure(ctx, "답변 처리 중 오류가 발생했습니다.");
        }
    }

    private void handleMeta(StreamCtx ctx, JsonNode node) {
        String subtype = node.path("subtype").asText();
        MetaEvent event = switch (subtype) {
            case "document" -> {
                String confidence = node.path("confidence").asText(null);
                List<SseSourceDto> sseSources = extractSources(node, ctx.sources());
                if (confidence != null) {
                    // AI가 스펙 외 문자열을 보내도 전체 스트림이 끊어지지 않도록 파싱 실패는 무시하고 null 유지
                    try {
                        ctx.confidence().set(ConfidenceLevel.from(confidence));
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown confidence level from AI server: {}", confidence);
                    }
                }
                yield MetaEvent.document(confidence, sseSources);
            }
            case "cache" -> MetaEvent.cache(extractSources(node, ctx.sources()));
            case "chitchat" -> MetaEvent.chitchat();
            default -> null;
        };
        if (event == null) {
            log.warn("Unknown meta subtype: {}", subtype);
            return;
        }
        trySend(ctx.emitter(), event, ctx.clientConnected());
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

    private void handleFallback(StreamCtx ctx, JsonNode node) {
        String message = node.path("message").asText("");
        List<String> suggestedQuestions = new ArrayList<>();
        JsonNode sqNode = node.path("suggested_questions");
        if (sqNode.isArray()) {
            for (JsonNode q : sqNode) {
                suggestedQuestions.add(q.asText());
            }
        }
        ctx.content().append(message);
        trySend(ctx.emitter(), FallbackEvent.of(message, suggestedQuestions), ctx.clientConnected());
    }

    private void handleText(StreamCtx ctx, JsonNode node) {
        String content = node.path("content").asText("");
        ctx.content().append(content);
        trySend(ctx.emitter(), TextEvent.of(content), ctx.clientConnected());
    }

    private void handleDone(StreamCtx ctx) {
        // 클라 연결 여부와 무관하게 DB 저장은 항상 수행 — 사용자가 돌아와서 히스토리에서 답변을 볼 수 있어야 한다
        Long messageId = persister.saveAssistantMessage(
                ctx.sessionId(),
                ctx.content().toString(),
                ctx.confidence().get(),
                ctx.sources(),
                MessageCompleteness.COMPLETE
        );
        trySend(ctx.emitter(), DoneEvent.of(messageId, ctx.rateLimit().remaining()), ctx.clientConnected());
        if (ctx.clientConnected().get()) {
            safeComplete(ctx.emitter());
        }
    }

    private void handleAiError(StreamCtx ctx, JsonNode node) {
        String message = node.path("message").asText("답변 생성 중 오류가 발생했습니다.");
        trySend(ctx.emitter(), ErrorEvent.of(message), ctx.clientConnected());
        if (ctx.clientConnected().get()) {
            safeComplete(ctx.emitter());
        }
    }

    private void handleStreamError(StreamCtx ctx, Throwable error) {
        // 이미 다른 경로(handleEvent catch 등)가 정리 중이라면 같은 로그·정리를 반복하지 않는다.
        if (ctx.terminalReceived().get()) return;
        log.error("AI server streaming error", error);
        finalizeUnexpectedFailure(ctx, "AI 서버와의 통신에 실패했습니다.");
    }

    private void handleStreamComplete(StreamCtx ctx) {
        if (ctx.terminalReceived().get()) {
            log.debug("AI server stream closed for session {}", ctx.sessionId());
            return;
        }
        // done/error 없이 스트림이 닫히면 FE는 타임아웃까지 매달려 있고 누적본도 유실된다 — 에러로 정리.
        log.warn("AI server stream closed without terminal event for session {}", ctx.sessionId());
        finalizeUnexpectedFailure(ctx, "AI 서버와의 통신이 비정상 종료됐습니다.");
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
