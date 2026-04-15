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

import java.util.List;

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
            for (AiSourceRaw src : sources) {
                if (src.chunkId() == null || src.docId() == null) {
                    log.warn("AI source missing identifiers, skipping: docId={}, chunkId={}",
                            src.docId(), src.chunkId());
                    continue;
                }
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
