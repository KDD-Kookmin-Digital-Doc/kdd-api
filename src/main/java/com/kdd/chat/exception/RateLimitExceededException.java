package com.kdd.chat.exception;

import lombok.Getter;

import java.time.Instant;

/**
 * 채팅 횟수 일일 한도 초과. 명세서의 429 응답이 {@code remaining}/{@code resetsAt} 필드를 요구하므로
 * 일반 {@link com.kdd.global.error.BusinessException}과 달리 별도 예외로 만들어 글로벌 핸들러에서
 * 전용 본문으로 직렬화한다.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final int remaining;
    private final Instant resetsAt;

    public RateLimitExceededException(int remaining, Instant resetsAt) {
        super("채팅 횟수 제한을 초과했습니다.");
        this.remaining = remaining;
        this.resetsAt = resetsAt;
    }
}
