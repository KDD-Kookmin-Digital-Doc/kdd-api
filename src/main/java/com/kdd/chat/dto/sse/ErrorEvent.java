package com.kdd.chat.dto.sse;

public record ErrorEvent(
        String type,
        String message
) {
    public static ErrorEvent of(String message) {
        return new ErrorEvent("error", message);
    }
}
