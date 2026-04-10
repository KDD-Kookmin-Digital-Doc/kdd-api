package com.kdd.user.dto;

import com.kdd.user.entity.*;

public record UserResponse(
        Long userId,
        String email,
        String name,
        String role,
        boolean profileCompleted,
        String userType,

        // 학생 전용
        String studentId,
        String department,
        Short grade,
        Short admissionYear,
        String academicStatus,
        String additionalInfo,

        // 교직원 전용
        String staffDepartment,
        String jobDescription
) {

    public static UserResponse from(User user, StudentProfile studentProfile, StaffProfile staffProfile) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole() != null ? user.getRole().getValue() : null,
                user.isProfileCompleted(),
                user.getUserType() != null ? user.getUserType().getValue() : null,
                studentProfile != null ? studentProfile.getStudentId() : null,
                studentProfile != null ? studentProfile.getDepartment().getValue() : null,
                studentProfile != null ? studentProfile.getGrade() : null,
                studentProfile != null ? studentProfile.getAdmissionYear() : null,
                studentProfile != null ? studentProfile.getAcademicStatus().getValue() : null,
                studentProfile != null ? studentProfile.getAdditionalInfo() : null,
                staffProfile != null ? staffProfile.getDepartment().getValue() : null,
                staffProfile != null ? staffProfile.getJobDescription() : null
        );
    }
}
