package com.kdd.faq.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * FAQ 후보 승인 요청 body.
 * <p>
 * 관리자가 FE에서 9개 topic 중 하나를 직접 선택해 보낸다 (요구사항 4-(3)-1 + FE RegisterModal).
 * AI가 후보 생성 시 분류해둔 값(candidate.topic)은 기본값/추천일 뿐이고, 최종 결정은 관리자 몫이다.
 * 필드명은 FaqResponse/FaqCandidateResponse와 일관되게 topic으로 통일.
 */
public record FaqCandidateApproveRequest(
        @NotBlank(message = "topic은 필수입니다.") String topic
) {
}
