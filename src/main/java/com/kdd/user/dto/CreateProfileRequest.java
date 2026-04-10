package com.kdd.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProfileRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @NotBlank(message = "사용자 유형은 필수입니다.")
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
}
