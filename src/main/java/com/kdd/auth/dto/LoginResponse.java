package com.kdd.auth.dto;

public record LoginResponse(
        String accessToken,
        boolean isNewUser
) {
}
