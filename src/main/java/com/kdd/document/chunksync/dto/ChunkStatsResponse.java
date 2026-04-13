package com.kdd.document.chunksync.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder({"total_docs", "total_chunks", "documents"})
public record ChunkStatsResponse(
        long totalDocs,
        long totalChunks,
        List<DocumentStatResponse> documents
) {
}
