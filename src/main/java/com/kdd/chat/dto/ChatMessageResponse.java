package com.kdd.chat.dto;

import com.kdd.chat.entity.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅 메시지 응답 단일 스키마. user/assistant 모두 동일 구조를 사용해 FE 메시지 렌더링 컴포넌트를 재사용한다.
 * ChatSessionDetailResponse·FaqChatStartResponse 공용.
 */
public record ChatMessageResponse(
        Long messageId,
        String role,
        String content,
        List<ChatMessageSourceResponse> sources,
        String confidence,
        boolean partial,
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
                message.isPartial(),
                message.getCreatedAt()
        );
    }
}
