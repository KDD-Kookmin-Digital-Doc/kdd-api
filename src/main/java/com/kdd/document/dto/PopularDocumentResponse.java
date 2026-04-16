package com.kdd.document.dto;

import com.kdd.document.repository.PopularDocumentProjection;

import java.time.LocalDateTime;

public record PopularDocumentResponse(
        Long documentId,
        String title,
        String category,
        int viewCount,
        int referenceCount,
        int popularityScore,
        LocalDateTime updatedAt
) {
    public static PopularDocumentResponse from(PopularDocumentProjection p) {
        return new PopularDocumentResponse(
                p.getId(),
                p.getTitle(),
                p.getCategoryName(),
                p.getViewCount(),
                p.getReferenceCount(),
                p.getPopularityScore(),
                p.getUpdatedAt()
        );
    }
}
