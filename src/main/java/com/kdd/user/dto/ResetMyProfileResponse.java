package com.kdd.user.dto;

import com.kdd.user.entity.User;

public record ResetMyProfileResponse(
        Long userId,
        String email,
        String role,
        boolean profileCompleted,
        boolean deletedStudentProfile,
        boolean deletedStaffProfile
) {

    public static ResetMyProfileResponse of(User user, int deletedStudent, int deletedStaff) {
        return new ResetMyProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().getValue() : null,
                user.isProfileCompleted(),
                deletedStudent > 0,
                deletedStaff > 0
        );
    }
}
