package com.kdd.chat.dto;

import com.kdd.faq.entity.FaqCandidate;

import java.util.List;

/**
 * 채팅 시작 전 추천 질문 응답 — 명세 §13 (채팅 시작 전 추천 질문 조회).
 * <p>
 * questionId는 명세상 string 타입이므로 {@link FaqCandidate#getId()}를 문자열로 직렬화하여 노출한다.
 * FE는 이 questionId를 그대로 {@code POST /faqs/{id}/chat-start} 와 동일한 흐름에 사용하지는 않으며,
 * 단순 식별자(중복 제거·트래킹) 용도다.
 * <p>
 * TOP 5 데이터 소스는 {@code faq_candidates} 테이블 — FAQ 후보 인입 스케줄러 결과와 동일하다 (명세 명시).
 */
public record RecommendedQuestionResponse(List<Question> questions) {

    public record Question(String questionId, String content) {
        public static Question from(FaqCandidate candidate) {
            return new Question(String.valueOf(candidate.getId()), candidate.getQuestion());
        }
    }

    public static RecommendedQuestionResponse from(List<FaqCandidate> candidates) {
        return new RecommendedQuestionResponse(
                candidates.stream().map(Question::from).toList()
        );
    }
}
