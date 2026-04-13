package com.kdd.document.chunksync.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder({"doc_name", "chunk_count"})
public record DocumentStatResponse(
        String docName,
        long chunkCount
) {
}
