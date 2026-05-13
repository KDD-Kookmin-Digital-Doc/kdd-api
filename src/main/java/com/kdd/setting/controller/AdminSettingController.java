package com.kdd.setting.controller;

import com.kdd.setting.dto.DefaultChatLimitResponse;
import com.kdd.setting.dto.DefaultChatLimitUpdateRequest;
import com.kdd.setting.service.AppSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Setting", description = "관리자 전역 설정 API")
@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class AdminSettingController {

    private final AppSettingService appSettingService;

    @Operation(summary = "신규 가입자 기본 채팅 한도 조회")
    @GetMapping("/default-chat-limit")
    public ResponseEntity<DefaultChatLimitResponse> getDefaultChatLimit() {
        return ResponseEntity.ok(new DefaultChatLimitResponse(appSettingService.getDefaultChatLimit()));
    }

    @Operation(summary = "신규 가입자 기본 채팅 한도 변경",
            description = "변경 즉시 새 가입자에게 적용된다. 기존 사용자의 개별 한도에는 영향을 주지 않는다.")
    @PatchMapping("/default-chat-limit")
    public ResponseEntity<DefaultChatLimitResponse> updateDefaultChatLimit(
            @Valid @RequestBody DefaultChatLimitUpdateRequest request) {
        int updated = appSettingService.updateDefaultChatLimit(request.defaultChatLimit());
        return ResponseEntity.ok(new DefaultChatLimitResponse(updated));
    }
}
