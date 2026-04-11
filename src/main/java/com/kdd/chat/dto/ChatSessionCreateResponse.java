package com.kdd.chat.dto;

import com.kdd.chat.entity.ChatSession;

import java.time.LocalDateTime;

public record ChatSessionCreateResponse(
        Long sessionId,
        String title,
        String sourceType,
        LocalDateTime createdAt
) {
    public static ChatSessionCreateResponse from(ChatSession session) {
        return new ChatSessionCreateResponse(
                session.getId(),
                session.getTitle(),
                session.getSourceType().getValue(),
                session.getCreatedAt()
        );
    }
}
