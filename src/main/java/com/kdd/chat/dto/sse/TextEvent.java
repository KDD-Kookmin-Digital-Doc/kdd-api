package com.kdd.chat.dto.sse;

public record TextEvent(
        String type,
        String content
) {
    public static TextEvent of(String content) {
        return new TextEvent("text", content);
    }
}
