package com.kdd.chat.controller;

import com.kdd.chat.dto.RecommendedQuestionResponse;
import com.kdd.faq.entity.FaqCandidate;
import com.kdd.faq.service.FaqCandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 채팅 시작 전 추천 질문 — 명세 §13.
 * <p>
 * BE 스케줄러({@code FaqCandidateScheduler})가 산출한 인기 질문 TOP 5를 그대로 노출한다.
 * 동일 데이터(faq_candidates 테이블의 PENDING 후보)를 FAQ 후보 검토와 공유하여 single source of truth를 유지한다.
 */
@Tag(name = "Chat - Recommendation", description = "채팅 시작 전 추천 질문 API")
@RestController
@RequiredArgsConstructor
public class ChatRecommendationController {

    // 명세 §13 "TOP 5" — 상수로 고정. 별도 외부화가 필요해지면 yml로 옮긴다.
    private static final int RECOMMENDED_LIMIT = 5;

    private final FaqCandidateService faqCandidateService;

    @Operation(summary = "채팅 시작 전 추천 질문 조회",
            description = "BE 스케줄러가 산출한 인기 질문 TOP 5를 반환한다. frequency 내림차순 정렬. " +
                    "관리자가 승인/반려한 후보는 자동으로 다음 추천에서 제외된다.")
    @GetMapping("/chat/recommended-questions")
    public ResponseEntity<RecommendedQuestionResponse> getRecommendedQuestions() {
        List<FaqCandidate> top = faqCandidateService.getRecommendedQuestions(RECOMMENDED_LIMIT);
        return ResponseEntity.ok(RecommendedQuestionResponse.from(top));
    }
}
