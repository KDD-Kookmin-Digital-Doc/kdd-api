package com.kdd.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    INVALID_AUTH_CODE(HttpStatus.UNAUTHORIZED, "INVALID_AUTH_CODE", "유효하지 않은 Google 인증 코드입니다."),
    UNAUTHORIZED_DOMAIN(HttpStatus.FORBIDDEN, "UNAUTHORIZED_DOMAIN", "허용되지 않은 이메일 도메인입니다."),
    UNVERIFIED_EMAIL(HttpStatus.FORBIDDEN, "UNVERIFIED_EMAIL", "이메일 인증이 완료되지 않았습니다."),
    ACCOUNT_DEACTIVATED(HttpStatus.FORBIDDEN, "ACCOUNT_DEACTIVATED", "비활성화된 계정입니다."),

    // Token
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_TOKEN", "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않거나 만료된 Refresh Token입니다."),

    // Document
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "존재하지 않는 문서입니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "존재하지 않는 카테고리입니다."),
    PARENT_CATEGORY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PARENT_CATEGORY_NOT_ALLOWED", "하위 카테고리로만 문서를 조회할 수 있습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "지원하지 않는 파일 형식입니다."),
    DOCUMENT_ALREADY_PROCESSING(HttpStatus.CONFLICT, "DOCUMENT_ALREADY_PROCESSING", "문서가 이미 처리 중입니다."),

    // User
    PROFILE_ALREADY_COMPLETED(HttpStatus.CONFLICT, "PROFILE_ALREADY_COMPLETED", "이미 프로필이 입력된 사용자입니다."),
    PROFILE_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "PROFILE_NOT_COMPLETED", "프로필 입력이 완료되지 않은 사용자입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 사용자입니다."),

    // Chat
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "존재하지 않는 채팅 세션입니다."),
    SESSION_FORBIDDEN(HttpStatus.FORBIDDEN, "SESSION_FORBIDDEN", "다른 사용자의 채팅 세션은 접근할 수 없습니다."),

    // Common
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자 권한이 필요합니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
