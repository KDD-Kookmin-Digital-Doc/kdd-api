package com.kdd.global.error;

import com.kdd.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${app.cors.secure-cookie}")
    private boolean secureCookie;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
    }
}
