package com.kdd.document.chunksync.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder({"chunks", "total", "page", "size", "total_pages"})
public record ChunkPagedResponse(
        List<ChunkResponse> chunks,
        long total,
        int page,
        int size,
        int totalPages
) {
}
