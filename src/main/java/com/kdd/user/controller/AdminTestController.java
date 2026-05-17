package com.kdd.user.controller;

import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.user.dto.ResetMyProfileResponse;
import com.kdd.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입 플로우 재테스트용 본인 프로필 리셋 컨트롤러.
 * <p>
 * {@code @Profile("!prod")} 가드로 운영 프로파일(prod)에서는 빈으로 등록되지 않아 컨트롤러 자체가 사라진다 —
 * 운영 admin이 실수/악의로 호출해 본인 프로필을 손실하는 경로를 컴파일·기동 시점부터 차단한다.
 * 로컬/개발/스테이징(dev, local, stage)에서만 활성화된다.
 */
@Tag(name = "Admin - Test", description = "관리자 테스트 전용 API (회원가입 플로우 재테스트 등). 운영(prod)에서는 비활성화.")
@RestController
@RequestMapping("/admin/test")
@RequiredArgsConstructor
@Profile("!prod")
public class AdminTestController {

    private static final String RESET_PROFILE_CONFIRM_TOKEN = "RESET-MY-PROFILE";

    private final UserService userService;

    @Operation(
            summary = "본인 프로필 리셋 (회원가입 재테스트용)",
            description = "호출자 본인의 student_profiles/staff_profiles 행을 삭제하고 users.is_profile_completed=false로 변경한다. " +
                    "users 행/role/로그인 세션은 유지된다. admin 계정만 호출 가능. " +
                    "회원가입 플로우(프로필 생성 페이지)를 반복 테스트할 때 사용. " +
                    "실수 호출 방지를 위해 confirm=RESET-MY-PROFILE 쿼리 파라미터가 반드시 필요하다."
    )
    @PostMapping("/reset-my-profile")
    public ResponseEntity<ResetMyProfileResponse> resetMyProfile(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "실수 호출 방지용 확인 토큰. 값은 RESET-MY-PROFILE 고정.", required = true)
            @RequestParam(required = false) String confirm) {
        if (!RESET_PROFILE_CONFIRM_TOKEN.equals(confirm)) {
            throw new BusinessException(ErrorCode.CONFIRMATION_REQUIRED);
        }
        ResetMyProfileResponse response = userService.resetMyProfile(userId);
        return ResponseEntity.ok(response);
    }
}
