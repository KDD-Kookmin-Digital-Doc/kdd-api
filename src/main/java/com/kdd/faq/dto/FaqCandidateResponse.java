package com.kdd.faq.dto;

import com.kdd.faq.entity.FaqCandidate;
import com.kdd.faq.entity.FaqCandidateStatus;
import com.kdd.faq.entity.FaqTopic;

import java.time.LocalDateTime;

/**
 * FAQ 후보 목록·상세 응답.
 * [BE] ERD FAQCandidate 컬럼 그대로 노출. 응답 필드명은 ERD 컬럼명을 카멜케이스로(answerDraft). topic은 nullable.
 * <p>
 * status는 요구사항 4-(3)-1 "관리자는 FAQ 후보를 반려할 수 있어야 한다 — 하지만 상태만 REJECTED로 변하고
 * 목록에서 제거되지는 않는다"에 따라 항상 응답에 포함된다. FE는 이 값으로 PENDING/APPROVED/REJECTED를
 * 구분해 검토 화면에서 처리한다.
 */
public record FaqCandidateResponse(
        Long candidateId,
        String question,
        String answerDraft,
        String topic,
        String status,
        LocalDateTime createdAt
) {
    public static FaqCandidateResponse from(FaqCandidate candidate) {
        FaqTopic category = candidate.getCategory();
        FaqCandidateStatus status = candidate.getStatus();
        return new FaqCandidateResponse(
                candidate.getId(),
                candidate.getQuestion(),
                candidate.getAnswerDraft(),
                category == null ? null : category.getValue(),
                status == null ? null : status.getValue(),
                candidate.getCreatedAt()
        );
    }
}
