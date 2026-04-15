package com.kdd.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * AI 서버 DELETE /api/documents/{doc_id} 응답.
 * 존재하지 않는 doc_id도 success 반환 (멱등성 보장).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiDeleteResponse(
        String status,
        String docId,
        int deletedChunkCount,
        int invalidatedCacheCount,
        String message
) {
}
