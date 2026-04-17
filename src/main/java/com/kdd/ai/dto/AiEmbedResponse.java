package com.kdd.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * AI 서버 POST /api/documents/embed 응답.
 * <p>
 * status 값:
 * <ul>
 *     <li>success — 모든 청크 임베딩 성공</li>
 *     <li>partial_failure — 일부 청크 실패 (failed_chunks 포함)</li>
 * </ul>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiEmbedResponse(
        String status,
        Long docId,
        int embeddedChunkCount,
        List<FailedChunk> failedChunks,
        String message
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FailedChunk(
            int index,
            String error
    ) {
    }

    public boolean isSuccess() {
        return "success".equals(status);
    }
}
