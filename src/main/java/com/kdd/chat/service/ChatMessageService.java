package com.kdd.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kdd.chat.dto.ai.AiChatRequest;
import com.kdd.chat.dto.sse.DoneEvent;
import com.kdd.chat.dto.sse.ErrorEvent;
import com.kdd.chat.dto.sse.FallbackEvent;
import com.kdd.chat.dto.sse.MetaEvent;
import com.kdd.chat.dto.sse.SseSourceDto;
import com.kdd.chat.dto.sse.TextEvent;
import com.kdd.chat.entity.ChatMessage;
import com.kdd.chat.entity.ChatSession;
import com.kdd.chat.entity.ConfidenceLevel;
import com.kdd.chat.repository.ChatMessageRepository;
import com.kdd.chat.repository.ChatSessionRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserContextBuilder userContextBuilder;
    private final ChatMessagePersister persister;
    private final WebClient aiServerWebClient;

    private static final long SSE_TIMEOUT_MS = 300_000L;

    public SseEmitter sendMessage(Long sessionId, Long userId, String content) {
        ContextData context = prepareContext(sessionId, userId);
        persister.saveUserMessage(sessionId, content);

        AiChatRequest request = new AiChatRequest(
                content,
                String.valueOf(sessionId),
                context.userContext(),
                context.history()
        );

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        streamFromAiServer(emitter, sessionId, request);
        return emitter;
    }

    private ContextData prepareContext(Long sessionId, Long userId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        User user = session.getUser();
        if (!user.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_FORBIDDEN);
        }
        String userContext = userContextBuilder.buildContext(user);

        List<AiChatRequest.HistoryEntry> history = chatMessageRepository
                .findTop10BySessionIdOrderByCreatedAtDescIdDesc(sessionId)
                .stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(m -> new AiChatRequest.HistoryEntry(m.getRole().getValue(), m.getContent()))
                .toList();

        return new ContextData(userContext, history);
    }

    private void streamFromAiServer(SseEmitter emitter, Long sessionId, AiChatRequest request) {
        StringBuilder contentAccumulator = new StringBuilder();
        AtomicReference<ConfidenceLevel> confidenceRef = new AtomicReference<>();
        List<AiSourceRaw> capturedSources = new ArrayList<>();

        Disposable disposable = aiServerWebClient.post()
                .uri("/api/chat")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        node -> handleEvent(emitter, node, sessionId,
                                contentAccumulator, confidenceRef, capturedSources),
                        error -> handleStreamError(emitter, error),
                        () -> log.debug("AI server stream closed for session {}", sessionId)
                );

        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for session {}", sessionId);
            disposable.dispose();
        });
        emitter.onError(err -> {
            log.warn("SSE error for session {}: {}", sessionId, err.getMessage());
            disposable.dispose();
        });
    }

    private void handleEvent(SseEmitter emitter, JsonNode node, Long sessionId,
                             StringBuilder contentAccumulator,
                             AtomicReference<ConfidenceLevel> confidenceRef,
                             List<AiSourceRaw> capturedSources) {
        try {
            String type = node.path("type").asText();
            switch (type) {
                case "meta" -> handleMeta(emitter, node, confidenceRef, capturedSources);
                case "fallback" -> handleFallback(emitter, node, contentAccumulator);
                case "text" -> handleText(emitter, node, contentAccumulator);
                case "done" -> handleDone(emitter, sessionId, contentAccumulator, confidenceRef, capturedSources);
                case "error" -> handleAiError(emitter, node);
                default -> log.warn("Unknown SSE event type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to process SSE event for session {}", sessionId, e);
            safeCompleteWithError(emitter, e);
        }
    }

    private void handleMeta(SseEmitter emitter, JsonNode node,
                            AtomicReference<ConfidenceLevel> confidenceRef,
                            List<AiSourceRaw> capturedSources) throws Exception {
        String subtype = node.path("subtype").asText();
        MetaEvent event = switch (subtype) {
            case "document" -> {
                String confidence = node.path("confidence").asText(null);
                Integer similarityScore = node.hasNonNull("similarity_score")
                        ? node.get("similarity_score").asInt() : null;
                List<SseSourceDto> sseSources = extractSources(node, capturedSources);
                if (confidence != null) {
                    try {
                        confidenceRef.set(ConfidenceLevel.from(confidence));
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown confidence level from AI server: {}", confidence);
                    }
                }
                yield MetaEvent.document(confidence, similarityScore, sseSources);
            }
            case "cache" -> MetaEvent.cache(extractSources(node, capturedSources));
            case "chitchat" -> MetaEvent.chitchat();
            default -> null;
        };
        if (event == null) {
            log.warn("Unknown meta subtype: {}", subtype);
            return;
        }
        emitter.send(SseEmitter.event().data(event));
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
                                StringBuilder contentAccumulator) throws Exception {
        String message = node.path("message").asText("");
        List<String> suggestedQuestions = new ArrayList<>();
        JsonNode sqNode = node.path("suggested_questions");
        if (sqNode.isArray()) {
            for (JsonNode q : sqNode) {
                suggestedQuestions.add(q.asText());
            }
        }
        contentAccumulator.append(message);
        emitter.send(SseEmitter.event().data(FallbackEvent.of(message, suggestedQuestions)));
    }

    private void handleText(SseEmitter emitter, JsonNode node,
                            StringBuilder contentAccumulator) throws Exception {
        String content = node.path("content").asText("");
        contentAccumulator.append(content);
        emitter.send(SseEmitter.event().data(TextEvent.of(content)));
    }

    private void handleDone(SseEmitter emitter, Long sessionId,
                            StringBuilder contentAccumulator,
                            AtomicReference<ConfidenceLevel> confidenceRef,
                            List<AiSourceRaw> capturedSources) throws Exception {
        Long messageId = persister.saveAssistantMessage(
                sessionId,
                contentAccumulator.toString(),
                confidenceRef.get(),
                capturedSources
        );
        emitter.send(SseEmitter.event().data(DoneEvent.of(messageId)));
        emitter.complete();
    }

    private void handleAiError(SseEmitter emitter, JsonNode node) throws Exception {
        String message = node.path("message").asText("답변 생성 중 오류가 발생했습니다.");
        emitter.send(SseEmitter.event().data(ErrorEvent.of(message)));
        emitter.complete();
    }

    private void handleStreamError(SseEmitter emitter, Throwable error) {
        log.error("AI server streaming error", error);
        try {
            emitter.send(SseEmitter.event().data(ErrorEvent.of("AI 서버와의 통신에 실패했습니다.")));
        } catch (Exception ignored) {
        }
        safeComplete(emitter);
    }

    private void safeCompleteWithError(SseEmitter emitter, Throwable error) {
        try {
            emitter.completeWithError(error);
        } catch (Exception ignored) {
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private record ContextData(
            String userContext,
            List<AiChatRequest.HistoryEntry> history
    ) {
    }
}
