package com.kdd.user.controller;

import com.kdd.chat.dto.ChatUsageResponse;
import com.kdd.chat.service.ChatRateLimitService;
import com.kdd.user.dto.CreateProfileRequest;
import com.kdd.user.dto.UpdateProfileRequest;
import com.kdd.user.dto.UserResponse;
import com.kdd.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ChatRateLimitService chatRateLimitService;

    @GetMapping
    public ResponseEntity<UserResponse> getMe(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserResponse response = userService.getMe(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chat-usage")
    public ResponseEntity<ChatUsageResponse> getMyChatUsage(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(chatRateLimitService.getUsage(userId));
    }

    @PostMapping("/profile")
    public ResponseEntity<UserResponse> createProfile(
            Authentication authentication,
            @Valid @RequestBody CreateProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        UserResponse response = userService.createProfile(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping
    public ResponseEntity<UserResponse> updateMe(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        UserResponse response = userService.updateMe(userId, request);
        return ResponseEntity.ok(response);
    }
}
