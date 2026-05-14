package com.kdd.chat.dto.sse;

public record SseSourceDto(
        Long documentId,
        String documentTitle,
        Integer page,
        Long chunkId
) {
}
