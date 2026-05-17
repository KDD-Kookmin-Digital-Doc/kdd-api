package com.kdd.faq.dto;

import com.kdd.faq.entity.FaqCandidate;
import com.kdd.faq.entity.FaqCandidateStatus;
import com.kdd.faq.entity.FaqTopic;

import java.time.LocalDateTime;

/**
 * FAQ 후보 목록·상세 응답.
 * [BE] ERD FAQCandidate 컬럼 + V7 마이그레이션으로 추가된 frequency를 그대로 노출.
 * <p>
 * 응답 필드명은 ERD 컬럼명을 카멜케이스로(answerDraft). topic/answerDraft는 nullable.
 * status는 요구사항 4-(3)-1 "반려해도 row는 보존, 목록에서 제거되지 않는다"에 따라 항상 노출 —
 * FE가 PENDING/APPROVED/REJECTED를 구분해 UI에서 처리할 수 있도록 한다.
 * frequency는 관리자 검토 화면에서 승인 우선순위(클러스터 빈도) 판단에 사용된다.
 */
public record FaqCandidateResponse(
        Long candidateId,
        String question,
        String answerDraft,
        String topic,
        String status,
        int frequency,
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
                candidate.getFrequency(),
                candidate.getCreatedAt()
        );
    }
}
