package com.kdd.chat.service;

import com.kdd.chat.dto.ai.AiChatRequest;
import com.kdd.chat.entity.ChatMessage;
import com.kdd.chat.entity.ChatMessageSource;
import com.kdd.chat.entity.ChatSession;
import com.kdd.chat.entity.ConfidenceLevel;
import com.kdd.chat.entity.MessageCompleteness;
import com.kdd.chat.entity.MessageRole;
import com.kdd.chat.repository.ChatMessageRepository;
import com.kdd.chat.repository.ChatMessageSourceRepository;
import com.kdd.chat.repository.ChatSessionRepository;
import com.kdd.document.entity.Document;
import com.kdd.document.entity.DocumentChunk;
import com.kdd.document.repository.DocumentChunkRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessagePersister {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageSourceRepository chatMessageSourceRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final UserContextBuilder userContextBuilder;

    /**
     * sendMessage 진입부에서 in-flight 락을 잡기 전에 호출된다 — 락은 sessionId만 보고 잡히므로,
     * 다른 사용자가 남의 sessionId로 호출해도 락이 잠깐 점유되어 진짜 owner가 자기 세션에서 간헐적으로
     * 409 CHAT_SESSION_BUSY를 받는 grief 시나리오를 차단한다.
     * <p>
     * 전체 엔티티 fetch 대신 user_id projection 쿼리만 돌려 cheap하게 검증.
     * {@link #prepareAndSaveUserMessage}에 동일한 체크가 남아 있는 것은 defense-in-depth — 이 메서드와
     * prepareAndSaveUserMessage 사이에 세션이 삭제되는 TOCTOU 갭에서도 후자가 SESSION_NOT_FOUND를 던지고
     * sendMessage의 catch가 rate-limit을 보상하므로 안전하게 처리된다.
     */
    @Transactional(readOnly = true)
    public void verifySessionOwnership(Long sessionId, Long userId) {
        Long ownerId = chatSessionRepository.findUserIdById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!ownerId.equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_FORBIDDEN);
        }
    }

    // SSE 스트리밍 시작 전에 필요한 모든 DB 작업(세션·유저 검증, 컨텍스트/히스토리 조회, 사용자 메시지 저장)을
    // 한 트랜잭션에 묶어 짧게 끝낸다. open-in-view=false 환경에서 ChatSession.user / 프로필 조회 등의 lazy
    // 로딩이 안전하게 일어나도록 하면서, 트랜잭션 종료와 동시에 커넥션을 풀로 반환해 SSE 수십 초 동안
    // 커넥션 점유가 발생하지 않게 한다.
    @Transactional
    public PreparedChat prepareAndSaveUserMessage(Long sessionId, Long userId, String content) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        User user = session.getUser();
        if (!user.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_FORBIDDEN);
        }
        String userContext = userContextBuilder.buildContext(user);

        // 사용자 메시지 저장 전에 히스토리 조회와 isFirstMessage 판정을 끝내야 한다.
        // Hibernate auto-flush로 save 이후 쿼리는 방금 저장한 메시지를 포함하게 되므로 순서가 중요.
        // DB는 최신 10개를 뽑기 위해 DESC로 조회하지만 AI는 대화 순서대로 ASC를 기대하므로 메모리에서 재정렬.
        // createdAt이 동일할 때 stable sort가 조회 순서(id DESC)를 유지하면 같은 시각 메시지가 역순이 되므로 id를 tie-breaker로 추가.
        List<ChatMessage> recentMessages = chatMessageRepository
                .findTop10BySessionIdOrderByCreatedAtDescIdDesc(sessionId);
        boolean isFirstMessage = recentMessages.isEmpty();
        List<AiChatRequest.HistoryEntry> history = recentMessages.stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt)
                        .thenComparing(ChatMessage::getId))
                .map(m -> new AiChatRequest.HistoryEntry(m.getRole().getValue(), m.getContent()))
                .toList();

        // AI 호출 실패·스트리밍 중단과 무관하게 사용자 입력은 히스토리로 남겨야 하므로 먼저 영속화
        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(content)
                .partial(false)
                .build());

        return new PreparedChat(userContext, history, isFirstMessage);
    }

    public record PreparedChat(
            String userContext,
            List<AiChatRequest.HistoryEntry> history,
            boolean isFirstMessage
    ) {
    }

    @Transactional
    public Long saveAssistantMessage(Long sessionId, String content,
                                     ConfidenceLevel confidence, List<AiSourceRaw> sources,
                                     MessageCompleteness completeness) {
        ChatSession session = chatSessionRepository.getReferenceById(sessionId);
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.ASSISTANT)
                .content(content)
                .confidenceLevel(confidence)
                .partial(completeness == MessageCompleteness.PARTIAL)
                .build());

        if (sources != null) {
            // (message_id, document_chunk_id) 유니크 제약 위반으로 트랜잭션 전체가 롤백되어
            // 답변이 유실되지 않도록, AI 응답 내 중복 chunk 참조는 첫 1건만 반영
            Set<Long> seenChunkIds = new HashSet<>();
            for (AiSourceRaw src : sources) {
                if (src.chunkId() == null || src.docId() == null) {
                    log.warn("AI source missing identifiers, skipping: docId={}, chunkId={}",
                            src.docId(), src.chunkId());
                    continue;
                }
                if (!seenChunkIds.add(src.chunkId())) {
                    log.warn("Duplicate source chunk in AI response, skipping: docId={}, chunkId={}",
                            src.docId(), src.chunkId());
                    continue;
                }
                // Chunk만 조회한 뒤 chunk.getDocument()로 Document를 얻으면 쿼리 수가 절반이 되고
                // AI가 보낸 docId-chunkId 조합이 실제 소속 관계와 일치하는지까지 함께 검증할 수 있음
                DocumentChunk chunk = documentChunkRepository.findById(src.chunkId()).orElse(null);
                if (chunk == null) {
                    log.warn("Source chunk not found in DB: docId={}, chunkId={}", src.docId(), src.chunkId());
                    continue;
                }
                Document doc = chunk.getDocument();
                if (doc == null || !doc.getId().equals(src.docId())) {
                    log.warn("Source doc/chunk mismatch: docId={}, chunkId={}, chunk.docId={}",
                            src.docId(), src.chunkId(), doc != null ? doc.getId() : null);
                    continue;
                }
                chatMessageSourceRepository.save(ChatMessageSource.builder()
                        .message(message)
                        .document(doc)
                        .documentChunk(chunk)
                        .chunkText(chunk.getContent())
                        .page(src.page())
                        .build());
            }
        }

        return message.getId();
    }
}
