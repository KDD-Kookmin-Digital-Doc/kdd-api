package com.kdd.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 서버 POST /api/documents/embed 요청 body.
 * BE가 청킹한 결과물을 AI에 전달하여 임베딩과 VectorDB 적재를 요청한다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiEmbedRequest(
        Long docId,
        Metadata metadata,
        List<Chunk> chunks
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Metadata(
            String docName,
            String category,
            LocalDate enforcementDate
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Chunk(
            Long chunkId,
            String content,
            int page
    ) {
    }
}
