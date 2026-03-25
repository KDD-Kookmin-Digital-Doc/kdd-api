package com.kdd.auth.controller;

import com.kdd.auth.dto.GoogleLoginRequest;
import com.kdd.auth.dto.LoginResponse;
import com.kdd.auth.service.AuthService;
import com.kdd.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int REFRESH_TOKEN_MAX_AGE = 14 * 24 * 60 * 60; // 14일

    private final AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {

        AuthService.LoginResult result = authService.login(request.code());

        ResponseCookie cookie = ResponseCookie.from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/auth")
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                .build();

        LoginResponse response = new LoginResponse(result.accessToken(), result.isNewUser());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(response));
    }
}
