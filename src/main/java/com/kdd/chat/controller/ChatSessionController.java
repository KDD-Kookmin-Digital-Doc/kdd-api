package com.kdd.chat.controller;

import com.kdd.chat.dto.*;
import com.kdd.chat.service.ChatSessionService;
import com.kdd.global.response.MessageResponse;
import com.kdd.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Chat Session", description = "채팅 세션 관리 API")
@RestController
@RequestMapping("/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @Operation(summary = "채팅 세션 생성", description = "새로운 채팅 세션을 생성한다. 세션 제목은 서버에서 날짜+시간 기반으로 자동 생성한다.")
    @PostMapping
    public ResponseEntity<ChatSessionCreateResponse> createSession(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatSessionService.createSession(userId));
    }

    @Operation(summary = "채팅 세션 목록 조회", description = "사용자의 채팅 세션 목록을 조회한다. 제목 키워드로 검색할 수 있다.")
    @GetMapping
    public ResponseEntity<PageResponse<ChatSessionListResponse>> getSessions(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(chatSessionService.getSessions(userId, keyword, page, pageSize));
    }

    @Operation(summary = "채팅 세션 상세 조회", description = "특정 채팅 세션의 메시지 목록을 조회한다.")
    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatSessionDetailResponse> getSessionDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(chatSessionService.getSessionDetail(sessionId, userId));
    }

    @Operation(summary = "채팅 세션 제목 수정", description = "채팅 세션 제목을 수정한다.")
    @PatchMapping("/{sessionId}")
    public ResponseEntity<ChatSessionUpdateResponse> updateSession(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatSessionUpdateRequest request) {
        return ResponseEntity.ok(chatSessionService.updateSession(sessionId, userId, request.title()));
    }

    @Operation(summary = "채팅 세션 삭제", description = "채팅 세션을 즉시 삭제한다.")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<MessageResponse> deleteSession(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(chatSessionService.deleteSession(sessionId, userId));
    }
}
