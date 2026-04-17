package com.kdd.document.dto;

import com.kdd.document.entity.Document;
import com.kdd.document.repository.SearchByPopularityProjection;

import java.time.LocalDateTime;

public record DocumentSearchResponse(
        Long documentId,
        String title,
        String category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DocumentSearchResponse from(Document document) {
        return new DocumentSearchResponse(
                document.getId(),
                document.getTitle(),
                document.getCategory().getName(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    public static DocumentSearchResponse from(SearchByPopularityProjection p) {
        return new DocumentSearchResponse(
                p.getId(),
                p.getTitle(),
                p.getCategoryName(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
