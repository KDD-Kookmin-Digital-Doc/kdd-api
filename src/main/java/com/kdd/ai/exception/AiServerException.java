package com.kdd.ai.exception;

/**
 * AI 서버 호출 실패 시 던져지는 런타임 예외.
 * DocumentService에서 catch하여 문서 상태를 FAILED로 전환하고
 * 응답 자체는 정상적으로 반환하도록 한다.
 */
public class AiServerException extends RuntimeException {

    public AiServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiServerException(String message) {
        super(message);
    }
}
