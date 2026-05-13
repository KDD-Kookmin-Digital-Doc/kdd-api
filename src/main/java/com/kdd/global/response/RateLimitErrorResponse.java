package com.kdd.global.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * 429 RATE_LIMIT_EXCEEDED 전용 응답 본문. 일반 {@link ErrorResponse}와 달리
 * 명세서가 요구하는 {@code remaining}, {@code resetsAt}을 함께 직렬화한다.
 */
public record RateLimitErrorResponse(
        String error,
        String message,
        int remaining,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant resetsAt
) {
}
