package com.kdd.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorDetail error;

    @Getter
    @Builder
    public static class ErrorDetail {
        private final String code;
        private final String message;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static ApiResponse<Void> ok() {
        return ApiResponse.<Void>builder().success(true).build();
    }

    public static ApiResponse<Void> error(String code, String message) {
        return ApiResponse.<Void>builder().success(false)
                .error(ErrorDetail.builder().code(code).message(message).build()).build();
    }
}
