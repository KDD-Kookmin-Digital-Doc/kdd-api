package com.kdd.faq.dto;

import com.kdd.faq.entity.FaqCandidate;
import com.kdd.faq.entity.FaqTopic;

import java.time.LocalDateTime;

/**
 * FAQ 후보 목록·상세 응답.
 * [BE] ERD FAQCandidate 컬럼 그대로 노출 (frequency는 ERD에 없으므로 응답에서도 제외).
 * 응답 필드명은 ERD 컬럼명을 카멜케이스로(answerDraft). topic은 nullable.
 * 서비스가 PENDING 상태만 반환하므로 응답에 status 필드는 두지 않는다 (APPROVED/REJECTED는 audit 보존).
 */
public record FaqCandidateResponse(
        Long candidateId,
        String question,
        String answerDraft,
        String topic,
        LocalDateTime createdAt
) {
    public static FaqCandidateResponse from(FaqCandidate candidate) {
        FaqTopic category = candidate.getCategory();
        return new FaqCandidateResponse(
                candidate.getId(),
                candidate.getQuestion(),
                candidate.getAnswerDraft(),
                category == null ? null : category.getValue(),
                candidate.getCreatedAt()
        );
    }
}
