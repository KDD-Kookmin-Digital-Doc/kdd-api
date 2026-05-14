package com.kdd.faq.dto;

import com.kdd.faq.entity.FaqCandidate;
import com.kdd.faq.entity.FaqCandidateStatus;
import com.kdd.faq.entity.FaqTopic;

import java.time.LocalDateTime;

/**
 * FAQ 후보 목록·상세 응답.
 * 응답 필드명은 FaqResponse와 동일한 'topic'을 사용한다 — 같은 도메인 enum(FaqTopic)을 다른 키로
 * 노출하면 FE가 두 매핑 테이블을 유지해야 한다. DB 컬럼은 'category'이지만 외부 계약은 'topic'으로 통일.
 * topic은 AI 분류 실패/미적용 시 null 그대로 내려간다 (관리자가 승인 시점에 지정).
 */
public record FaqCandidateResponse(
        Long candidateId,
        String question,
        String draftAnswer,
        Integer frequency,
        String topic,
        String status,
        LocalDateTime createdAt
) {
    public static FaqCandidateResponse from(FaqCandidate candidate) {
        return new FaqCandidateResponse(
                candidate.getId(),
                candidate.getQuestion(),
                candidate.getDraftAnswer(),
                candidate.getFrequency(),
                topicValue(candidate.getCategory()),
                statusValue(candidate.getStatus()),
                candidate.getCreatedAt()
        );
    }

    private static String topicValue(FaqTopic topic) {
        return topic == null ? null : topic.getValue();
    }

    private static String statusValue(FaqCandidateStatus status) {
        return status == null ? null : status.getValue();
    }
}
