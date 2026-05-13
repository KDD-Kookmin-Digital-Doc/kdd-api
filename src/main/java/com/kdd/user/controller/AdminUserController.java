package com.kdd.user.controller;

import com.kdd.chat.dto.ChatUsageResponse;
import com.kdd.chat.service.ChatRateLimitService;
import com.kdd.global.response.PageResponse;
import com.kdd.user.dto.AdminUserListItemResponse;
import com.kdd.user.dto.BulkChatLimitUpdateRequest;
import com.kdd.user.dto.BulkChatLimitUpdateResponse;
import com.kdd.user.dto.ChatLimitUpdateRequest;
import com.kdd.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - User", description = "관리자 사용자/채팅 한도 관리 API")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final ChatRateLimitService chatRateLimitService;

    @Operation(summary = "사용자 목록 조회",
            description = "사용자 유형/역할/이름·이메일 검색으로 필터링한 사용자 목록과 각자의 일일 한도/오늘 사용량을 반환한다.")
    @GetMapping
    public ResponseEntity<PageResponse<AdminUserListItemResponse>> getUsers(
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return ResponseEntity.ok(adminUserService.searchUsers(userType, role, search, page, size));
    }

    @Operation(summary = "특정 사용자 일일 채팅 한도 변경")
    @PatchMapping("/{userId}/chat-limit")
    public ResponseEntity<AdminUserListItemResponse> updateChatLimit(
            @PathVariable Long userId,
            @Valid @RequestBody ChatLimitUpdateRequest request) {
        return ResponseEntity.ok(adminUserService.updateChatLimit(userId, request.dailyChatLimit()));
    }

    @Operation(summary = "여러 사용자 일일 채팅 한도 일괄 변경",
            description = "사용자 ID 배열을 받아 동일 한도를 일괄 적용한다. 존재하지 않는 ID는 조용히 건너뛴다.")
    @PatchMapping("/chat-limit/bulk")
    public ResponseEntity<BulkChatLimitUpdateResponse> bulkUpdateChatLimit(
            @Valid @RequestBody BulkChatLimitUpdateRequest request) {
        return ResponseEntity.ok(
                adminUserService.bulkUpdateChatLimit(request.userIds(), request.dailyChatLimit()));
    }

    @Operation(summary = "특정 사용자 오늘 사용량 초기화",
            description = "운영 중 민원/버그 대응 목적. 오늘자 사용량을 0으로 만든다.")
    @PostMapping("/{userId}/chat-usage/reset")
    public ResponseEntity<ChatUsageResponse> resetChatUsage(@PathVariable Long userId) {
        return ResponseEntity.ok(chatRateLimitService.resetTodayUsage(userId));
    }
}
