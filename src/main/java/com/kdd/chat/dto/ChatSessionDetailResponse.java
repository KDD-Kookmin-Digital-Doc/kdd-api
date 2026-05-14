package com.kdd.chat.dto;

import com.kdd.chat.entity.ChatMessage;
import com.kdd.chat.entity.ChatMessageSource;
import com.kdd.chat.entity.ChatSession;

import java.time.LocalDateTime;
import java.util.List;

public record ChatSessionDetailResponse(
        Long sessionId,
        String title,
        String sourceType,
        List<ChatMessageResponse> messages
) {
    public static ChatSessionDetailResponse from(ChatSession session) {
        return new ChatSessionDetailResponse(
                session.getId(),
                session.getTitle(),
                session.getSourceType().getValue(),
                session.getMessages().stream()
                        .map(ChatMessageResponse::from)
                        .toList()
        );
    }

    public record ChatMessageResponse(
            Long messageId,
            String role,
            String content,
            List<ChatMessageSourceResponse> sources,
            String confidence,
            LocalDateTime createdAt
    ) {
        public static ChatMessageResponse from(ChatMessage message) {
            return new ChatMessageResponse(
                    message.getId(),
                    message.getRole().getValue(),
                    message.getContent(),
                    message.getSources().stream()
                            .map(ChatMessageSourceResponse::from)
                            .toList(),
                    message.getConfidenceLevel() != null ? message.getConfidenceLevel().getValue() : null,
                    message.getCreatedAt()
            );
        }
    }

    public record ChatMessageSourceResponse(
            Long documentId,
            String documentTitle,
            Integer page,
            Long chunkId
    ) {
        public static ChatMessageSourceResponse from(ChatMessageSource source) {
            return new ChatMessageSourceResponse(
                    source.getDocument().getId(),
                    source.getDocument().getTitle(),
                    source.getPage(),
                    source.getDocumentChunk().getId()
            );
        }
    }
}
