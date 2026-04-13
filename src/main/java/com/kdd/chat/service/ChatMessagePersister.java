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
import com.kdd.document.repository.DocumentRepository;
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
    private final DocumentRepository documentRepository;
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
                Document doc = documentRepository.findById(src.docId()).orElse(null);
                DocumentChunk chunk = documentChunkRepository.findById(src.chunkId()).orElse(null);
                if (doc == null || chunk == null) {
                    log.warn("Source not found in DB: docId={}, chunkId={}", src.docId(), src.chunkId());
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
