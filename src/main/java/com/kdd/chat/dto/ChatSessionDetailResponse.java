package com.kdd.chat.dto;

import com.kdd.chat.entity.ChatSession;

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
}
