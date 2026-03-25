package com.kdd.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Google 인증 코드는 필수입니다.")
        String code
) {
}
