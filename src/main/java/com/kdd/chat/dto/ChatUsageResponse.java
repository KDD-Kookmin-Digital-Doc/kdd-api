package com.kdd.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record ChatUsageResponse(
        int dailyChatLimit,
        int todayUsed,
        int remaining,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant resetsAt
) {
}
