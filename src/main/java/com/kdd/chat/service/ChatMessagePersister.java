package com.kdd.chat.service;

import com.kdd.chat.entity.ChatMessage;
import com.kdd.chat.entity.ChatMessageSource;
import com.kdd.chat.entity.ChatSession;
import com.kdd.chat.entity.ConfidenceLevel;
import com.kdd.chat.entity.MessageRole;
import com.kdd.chat.repository.ChatMessageRepository;
import com.kdd.chat.repository.ChatMessageSourceRepository;
import com.kdd.chat.repository.ChatSessionRepository;
import com.kdd.document.entity.Document;
import com.kdd.document.entity.DocumentChunk;
import com.kdd.document.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public Long saveUserMessage(Long sessionId, String content) {
        // 세션 존재는 상위 레이어에서 이미 검증했으므로 FK 주입 용도로만 프록시 참조 (불필요한 SELECT 회피)
        ChatSession session = chatSessionRepository.getReferenceById(sessionId);
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(content)
                .build());
        return message.getId();
    }

    @Transactional
    public Long saveAssistantMessage(Long sessionId, String content,
                                     ConfidenceLevel confidence, List<AiSourceRaw> sources) {
        ChatSession session = chatSessionRepository.getReferenceById(sessionId);
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.ASSISTANT)
                .content(content)
                .confidenceLevel(confidence)
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
