package com.kdd.chat.dto.sse;

public record DoneEvent(
        String type,
        Long messageId,
        int remaining
) {
    public static DoneEvent of(Long messageId, int remaining) {
        return new DoneEvent("done", messageId, remaining);
    }
}
