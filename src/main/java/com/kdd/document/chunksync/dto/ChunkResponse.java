package com.kdd.document.chunksync.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.kdd.document.entity.Document;
import com.kdd.document.entity.DocumentChunk;

import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder({
        "id", "content", "doc_name", "section_path", "page",
        "has_table", "source_url", "category", "created_at"
})
public record ChunkResponse(
        String id,
        String content,
        String docName,
        String sectionPath,
        int page,
        boolean hasTable,
        String sourceUrl,
        String category,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {
    public static ChunkResponse from(DocumentChunk chunk) {
        Document doc = chunk.getDocument();
        return new ChunkResponse(
                String.valueOf(chunk.getId()),
                chunk.getContent(),
                doc.getOriginalFilename(),
                chunk.getSectionPath() == null ? "본문" : chunk.getSectionPath(),
                chunk.getPage() == null ? 1 : chunk.getPage(),
                chunk.isHasTable(),
                doc.getOriginalUrl() == null ? "" : doc.getOriginalUrl(),
                doc.getCategory() == null ? "" : doc.getCategory().getName(),
                chunk.getCreatedAt()
        );
    }
}
