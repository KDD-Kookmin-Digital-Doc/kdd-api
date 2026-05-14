package com.kdd.faq.dto;

import com.kdd.faq.entity.FaqCandidate;
import com.kdd.faq.entity.FaqTopic;

import java.time.LocalDateTime;

/**
 * FAQ 후보 목록·상세 응답.
 * [BE] ERD FAQCandidate 컬럼을 그대로 노출 (frequency는 ERD에 없으므로 응답에서도 제외).
 * answer_draft / category는 nullable, 응답 필드명은 ERD 컬럼명을 카멜케이스로 (answerDraft).
 */
public record FaqCandidateResponse(
        Long candidateId,
        String question,
        String answerDraft,
        String topic,
        LocalDateTime createdAt
) {
    public static FaqCandidateResponse from(FaqCandidate candidate) {
        return new FaqCandidateResponse(
                candidate.getId(),
                candidate.getQuestion(),
                candidate.getAnswerDraft(),
                topicValue(candidate.getCategory()),
                candidate.getCreatedAt()
        );
    }

    private static String topicValue(FaqTopic topic) {
        return topic == null ? null : topic.getValue();
    }
}
