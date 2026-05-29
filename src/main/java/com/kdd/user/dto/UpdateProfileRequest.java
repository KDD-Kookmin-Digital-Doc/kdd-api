package com.kdd.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        // 공통
        String name,

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
