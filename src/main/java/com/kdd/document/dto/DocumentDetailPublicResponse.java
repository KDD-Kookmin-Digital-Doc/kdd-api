package com.kdd.document.dto;

import com.kdd.document.entity.Document;

import java.time.LocalDateTime;

public record DocumentDetailPublicResponse(
        Long documentId,
        String title,
        String category,
        String fileUrl,
        int viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DocumentDetailPublicResponse from(Document document) {
        return new DocumentDetailPublicResponse(
                document.getId(),
                document.getTitle(),
                document.getCategory().getName(),
                document.getStorageKey(),
                document.getViewCount(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
