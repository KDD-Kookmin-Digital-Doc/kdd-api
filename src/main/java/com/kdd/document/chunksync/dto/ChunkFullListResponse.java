package com.kdd.document.chunksync.dto;

import java.util.List;

public record ChunkFullListResponse(
        List<ChunkResponse> chunks,
        long total
) {
}
