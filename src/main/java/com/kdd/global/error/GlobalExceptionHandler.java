package com.kdd.global.error;

import com.kdd.chat.exception.RateLimitExceededException;
import com.kdd.global.response.ErrorResponse;
import com.kdd.global.response.RateLimitErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
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

    // 동일 세션 진행 중 AI 스트림이 끝날 때까지 클라가 polling/backoff 할 수 있도록 힌트.
    // 정상적인 AI 응답은 수 초~수십 초 단위로 끝나므로 typical case에 맞춘 짧은 값을 권한다 — worst case(AI 서버 멈춤)
    // 까지 기다리게 하면 FE가 과도하게 백오프해 UX가 나빠진다. doFinally가 실제 종료 즉시 가드를 풀어주므로
    // 클라가 너무 일찍 폴링해도 실제 락은 그 사이에 풀려 있을 확률이 높다.
    private static final String CHAT_SESSION_BUSY_RETRY_AFTER_SECONDS = "10";

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
        } else if (errorCode == ErrorCode.CHAT_SESSION_BUSY) {
            builder.header(HttpHeaders.RETRY_AFTER, CHAT_SESSION_BUSY_RETRY_AFTER_SECONDS);
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
     * @Validated 가 붙은 컨트롤러의 @RequestParam/@PathVariable 제약(@Min 등) 위반 시 발생.
     * 핸들러가 없으면 일반 Exception 핸들러로 떨어져 500이 응답된다 (예: page=-1).
     * MethodArgumentNotValidException 핸들러와 동일하게 위반된 파라미터별 메시지를 합쳐서 응답.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        if (message.isEmpty()) {
            message = ErrorCode.INVALID_INPUT.getMessage();
        }
        log.warn("Constraint violation: {}", message);
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(ErrorCode.INVALID_INPUT.getCode(), message));
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

    /**
     * PESSIMISTIC_WRITE 락 충돌(예: FAQ 후보 동시 승인). PostgreSQL의 lock_timeout 초과 또는
     * 다른 트랜잭션이 같은 row를 점유한 경우 → 409 LOCK_CONFLICT로 응답해 클라이언트가
     * 재시도 가능한 충돌과 진짜 서버 버그(500)를 구분할 수 있게 한다.
     */
    @ExceptionHandler({PessimisticLockingFailureException.class, CannotAcquireLockException.class})
    public ResponseEntity<ErrorResponse> handleLockConflict(Exception e) {
        // 충돌 시 어떤 쿼리/트랜잭션이었는지 추적 가능하도록 throwable 자체를 전달해 stack trace를 남긴다.
        log.warn("Lock conflict during concurrent operation", e);
        ErrorCode errorCode = ErrorCode.LOCK_CONFLICT;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
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
