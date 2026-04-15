package com.kdd.chat.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiChatRequest(
        String message,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("user_context") String userContext,
        List<HistoryEntry> history
) {
    public record HistoryEntry(String role, String content) {
    }
}
