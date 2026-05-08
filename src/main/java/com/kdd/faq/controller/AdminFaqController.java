package com.kdd.faq.controller;

import com.kdd.faq.dto.FaqCreateRequest;
import com.kdd.faq.dto.FaqResponse;
import com.kdd.faq.dto.FaqUpdateRequest;
import com.kdd.faq.service.FaqService;
import com.kdd.global.response.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - FAQ", description = "관리자 FAQ 관리 API")
@RestController
@RequestMapping("/admin/faqs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminFaqController {

    private final FaqService faqService;

    @Operation(summary = "FAQ 수동 등록", description = "관리자가 FAQ를 직접 등록한다.")
    @PostMapping
    public ResponseEntity<FaqResponse> create(@Valid @RequestBody FaqCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(faqService.create(request));
    }

    @Operation(summary = "FAQ 수정", description = "관리자가 등록된 FAQ의 질문/답변/토픽을 수정한다.")
    @PatchMapping("/{faqId}")
    public ResponseEntity<FaqResponse> update(
            @PathVariable Long faqId,
            @Valid @RequestBody FaqUpdateRequest request) {
        return ResponseEntity.ok(faqService.update(faqId, request));
    }

    @Operation(summary = "FAQ 삭제", description = "관리자가 등록된 FAQ를 삭제한다.")
    @DeleteMapping("/{faqId}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long faqId) {
        faqService.delete(faqId);
        return ResponseEntity.ok(new MessageResponse("FAQ가 삭제되었습니다."));
    }
}
