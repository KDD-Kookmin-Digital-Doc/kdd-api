package com.kdd.user.controller;

import com.kdd.user.dto.ResetMyProfileResponse;
import com.kdd.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Test", description = "관리자 테스트 전용 API (회원가입 플로우 재테스트 등)")
@RestController
@RequestMapping("/admin/test")
@RequiredArgsConstructor
public class AdminTestController {

    private final UserService userService;

    @Operation(
            summary = "본인 프로필 리셋 (회원가입 재테스트용)",
            description = "호출자 본인의 student_profiles/staff_profiles 행을 삭제하고 users.is_profile_completed=false로 변경한다. " +
                    "users 행/role/로그인 세션은 유지된다. admin 계정만 호출 가능. " +
                    "회원가입 플로우(프로필 생성 페이지)를 반복 테스트할 때 사용."
    )
    @PostMapping("/reset-my-profile")
    public ResponseEntity<ResetMyProfileResponse> resetMyProfile(@AuthenticationPrincipal Long userId) {
        ResetMyProfileResponse response = userService.resetMyProfile(userId);
        return ResponseEntity.ok(response);
    }
}
