package com.kdd.user.dto;

import com.kdd.user.entity.User;

public record AdminUserListItemResponse(
        Long id,
        String email,
        String name,
        String role,
        String userType,
        int dailyChatLimit,
        int todayUsed
) {
    public static AdminUserListItemResponse from(User user, int todayUsed) {
        return new AdminUserListItemResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole() != null ? user.getRole().getValue() : null,
                user.getUserType() != null ? user.getUserType().getValue() : null,
                user.getDailyChatLimit(),
                todayUsed
        );
    }
}
