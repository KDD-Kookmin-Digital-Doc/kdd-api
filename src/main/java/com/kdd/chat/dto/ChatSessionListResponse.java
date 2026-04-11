package com.kdd.chat.dto;

import com.kdd.chat.entity.ChatSession;

import java.time.LocalDateTime;

public record ChatSessionListResponse(
        Long sessionId,
        String title,
        String sourceType,
        LocalDateTime createdAt
) {
    public static ChatSessionListResponse from(ChatSession session) {
        return new ChatSessionListResponse(
                session.getId(),
                session.getTitle(),
                session.getSourceType().getValue(),
                session.getCreatedAt()
        );
    }
}
