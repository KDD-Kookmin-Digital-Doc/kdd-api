package com.kdd.chat.dto;

import com.kdd.chat.entity.ChatMessageSource;

/**
 * 채팅 답변의 출처 문서 정보. ChatSessionDetailResponse·FaqChatStartResponse 등 여러 응답에서 공유한다.
 */
public record ChatMessageSourceResponse(
        Long documentId,
        String documentTitle,
        Integer page
) {
    public static ChatMessageSourceResponse from(ChatMessageSource source) {
        return new ChatMessageSourceResponse(
                source.getDocument().getId(),
                source.getDocument().getTitle(),
                source.getPage()
        );
    }
}
