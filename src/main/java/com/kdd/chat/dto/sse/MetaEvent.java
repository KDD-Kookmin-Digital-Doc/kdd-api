package com.kdd.chat.dto.sse;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetaEvent(
        String type,
        String subtype,
        String confidence,
        List<SseSourceDto> sources
) {
    public static MetaEvent document(String confidence, List<SseSourceDto> sources) {
        return new MetaEvent("meta", "document", confidence, sources);
    }

    public static MetaEvent cache(List<SseSourceDto> sources) {
        return new MetaEvent("meta", "cache", null, sources);
    }

    public static MetaEvent chitchat() {
        return new MetaEvent("meta", "chitchat", null, null);
    }
}
