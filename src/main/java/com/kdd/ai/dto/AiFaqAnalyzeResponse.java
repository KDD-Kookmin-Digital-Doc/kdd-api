package com.kdd.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * AI 서버 POST /api/faq/analyze 응답.
 * <p>
 * status 값:
 * <ul>
 *     <li>success — 클러스터링 + 답변 초안 생성 성공</li>
 *     <li>error — 데이터 부족(INSUFFICIENT_DATA) 또는 LLM 통신 장애 — message에 사유</li>
 * </ul>
 * AI 응답에 candidates가 null인 경우(error 응답)도 안전하게 처리하도록 호출부에서 null 가드 필수.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiFaqAnalyzeResponse(
        String status,
        List<Candidate> candidates,
        String message
) {

    /**
     * category는 AI 서버가 분류한 FAQ 토픽 키 (academic, graduation, enrollment_status, scholarship,
     * registration, curriculum, career, event, other). LLM 분류 실패 시 AI가 "other"로 폴백하지만
     * 응답 자체가 누락되거나 변조됐을 가능성에 대비해 호출부에서 null/unknown 가드 필수.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(
            String question,
            String draftAnswer,
            String category,
            Integer frequency
    ) {
    }

    public boolean isSuccess() {
        return "success".equals(status);
    }
}
