package com.kdd.document.dto;

import com.kdd.document.entity.Document;

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
    public static PopularDocumentResponse from(Document document) {
        int viewCount = document.getViewCount();
        int referenceCount = 0;
        int popularityScore = viewCount + referenceCount;
        return new PopularDocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getCategory().getName(),
                viewCount,
                referenceCount,
                popularityScore,
                document.getUpdatedAt()
        );
    }
}
