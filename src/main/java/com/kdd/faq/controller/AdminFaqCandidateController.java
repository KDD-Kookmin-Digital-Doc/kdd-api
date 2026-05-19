package com.kdd.faq.controller;

import com.kdd.faq.dto.FaqCandidateApproveRequest;
import com.kdd.faq.dto.FaqCandidateResponse;
import com.kdd.faq.dto.FaqResponse;
import com.kdd.faq.entity.FaqCandidateStatus;
import com.kdd.faq.service.FaqCandidateService;
import com.kdd.global.response.MessageResponse;
import com.kdd.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - FAQ Candidates", description = "관리자 FAQ 후보 검토 API")
@RestController
@RequestMapping("/admin/faqs/candidates")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminFaqCandidateController {

    private final FaqCandidateService faqCandidateService;

    @Operation(summary = "FAQ 후보 목록 조회",
            description = "BE 스케줄러(FaqCandidateScheduler)가 인입한 FAQ 후보 목록을 page=0 기반 페이지네이션으로 조회한다. " +
                    "요구사항 4-(3)-1: 반려된 후보도 목록에서 제거되지 않으므로 기본은 PENDING/APPROVED/REJECTED 전체. " +
                    "status=pending|approved|rejected 쿼리 파라미터로 단일 상태 필터링이 가능하다. 최신순 정렬.")
    @GetMapping
    public ResponseEntity<PageResponse<FaqCandidateResponse>> getCandidates(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // status는 optional: null이면 전체 상태, 빈/공백도 동일하게 전체로 취급.
        FaqCandidateStatus parsedStatus = FaqCandidateStatus.parseOrNull(status);
        return ResponseEntity.ok(faqCandidateService.getCandidates(parsedStatus, page, pageSize));
    }

    @Operation(summary = "FAQ 후보 승인",
            description = "관리자가 카테고리를 지정해 FAQ 후보를 실제 FAQ로 등록한다. PENDING 상태만 가능.\n" +
                    "요청 body는 {\"topic\": \"academic\" | ... | \"other\"} 형식 — 요구사항 명세서의 " +
                    "'관리자는 각 FAQ 후보에 대해 FAQ 카테고리를 지정할 수 있어야 한다' 항목 충족을 위해 body 필수.")
    @PostMapping("/{candidateId}/approve")
    public ResponseEntity<FaqResponse> approve(
            @PathVariable Long candidateId,
            @Valid @RequestBody FaqCandidateApproveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(faqCandidateService.approve(candidateId, request));
    }

    @Operation(summary = "FAQ 후보 반려",
            description = "FAQ 후보를 반려한다. 상태만 REJECTED로 변하고 row는 보존된다.")
    @PatchMapping("/{candidateId}/reject")
    public ResponseEntity<MessageResponse> reject(@PathVariable Long candidateId) {
        faqCandidateService.reject(candidateId);
        return ResponseEntity.ok(new MessageResponse("FAQ 후보가 반려되었습니다."));
    }
}
