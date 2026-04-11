package com.kdd.chat.dto;

import com.kdd.chat.entity.ChatSession;

public record ChatSessionUpdateResponse(
        Long sessionId,
        String title
) {
    public static ChatSessionUpdateResponse from(ChatSession session) {
        return new ChatSessionUpdateResponse(
                session.getId(),
                session.getTitle()
        );
    }
}
