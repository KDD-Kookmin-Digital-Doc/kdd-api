package com.kdd.chat.dto.sse;

public record DoneEvent(
        String type,
        Long messageId
) {
    public static DoneEvent of(Long messageId) {
        return new DoneEvent("done", messageId);
    }
}
