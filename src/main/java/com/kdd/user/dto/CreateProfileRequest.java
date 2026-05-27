package com.kdd.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
        @Size(max = 250, message = "추가 정보는 250자 이하여야 합니다.")
        String additionalInfo,

        // 교직원 전용
        String staffDepartment,
        @Size(max = 250, message = "담당 업무는 250자 이하여야 합니다.")
        String jobDescription
) {
}
