package com.kdd.user.dto;

public record UpdateProfileRequest(
        // 공통
        String name,

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
}
