package com.kdd.global.error;

import com.kdd.chat.exception.RateLimitExceededException;
import com.kdd.global.response.ErrorResponse;
import com.kdd.global.response.RateLimitErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${app.cors.secure-cookie}")
    private boolean secureCookie;

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<RateLimitErrorResponse> handleRateLimit(RateLimitExceededException e) {
        log.info("Rate limit exceeded: remaining={}, resetsAt={}", e.getRemaining(), e.getResetsAt());
        ErrorCode code = ErrorCode.RATE_LIMIT_EXCEEDED;
        return ResponseEntity.status(code.getStatus())
                .body(new RateLimitErrorResponse(
                        code.getCode(),
                        code.getMessage(),
                        e.getRemaining(),
                        e.getResetsAt()
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: {}", errorCode.getMessage());

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.getStatus());

        if (errorCode == ErrorCode.INVALID_REFRESH_TOKEN) {
            ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                    .httpOnly(true)
                    .secure(secureCookie)
                    .sameSite("Strict")
                    .path("/auth")
                    .maxAge(0)
                    .build();
            builder.header(HttpHeaders.SET_COOKIE, expiredCookie.toString());
        }

        return builder.body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        if (message.isEmpty()) {
            message = ErrorCode.INVALID_INPUT.getMessage();
        }
        log.warn("Validation failed: {}", message);
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(ErrorCode.INVALID_INPUT.getCode(), message));
    }

    /**
     * @RequestParam/@PathVariable 타입 변환 실패 (예: enum value 잘못된 값) 또는
     * @RequestBody JSON 역직렬화 실패 → 일관되게 400 INVALID_INPUT으로 매핑.
     * 핸들러가 없으면 일반 Exception 핸들러로 떨어져 500이 응답된다.
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage()));
    }

    /**
     * 업로드 파일 크기가 {@code spring.servlet.multipart.max-file-size} 초과 시
     * Spring 이 던지는 예외 → 의미상 413 PAYLOAD_TOO_LARGE 로 응답 (#61).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Upload size exceeded: {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.PAYLOAD_TOO_LARGE;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * multipart `file` / `data` part 누락 또는 그 외 multipart 파싱 실패 → 400 INVALID_INPUT (#61).
     * 핸들러가 없으면 일반 Exception 으로 떨어져 500 응답되어 클라이언트가 진짜 서버 오류와 구분 불가.
     */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> handleMultipart(Exception e) {
        log.warn("Multipart request invalid: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
    }
}
