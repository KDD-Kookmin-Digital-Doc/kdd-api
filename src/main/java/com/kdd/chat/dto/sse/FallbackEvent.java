package com.kdd.chat.dto.sse;

import java.util.List;

public record FallbackEvent(
        String type,
        String message,
        List<String> suggestedQuestions
) {
    public static FallbackEvent of(String message, List<String> suggestedQuestions) {
        return new FallbackEvent("fallback", message, suggestedQuestions);
    }
}
