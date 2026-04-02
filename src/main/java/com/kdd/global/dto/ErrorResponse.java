package com.kdd.global.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {

    private final String error;
    private final String message;

    public static ErrorResponse of(String error, String message) {
        return ErrorResponse.builder().error(error).message(message).build();
    }
}
