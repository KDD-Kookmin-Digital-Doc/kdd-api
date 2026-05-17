package com.kdd.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * AI 서버 POST /api/faq/analyze 요청 body.
 * BE가 최근 사용자 질문 배열과 클러스터링 파라미터를 전달하면, AI는 인기 질문 top_k개와 답변 초안을 산출한다.
 * snake_case 직렬화는 다른 AI DTO와 동일 컨벤션 ({@link AiEmbedRequest}).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiFaqAnalyzeRequest(
        List<String> questions,
        int topK,
        int minClusterSize
) {
}
