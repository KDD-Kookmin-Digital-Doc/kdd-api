package com.kdd.document.dto;

import com.kdd.document.entity.Document;

import java.time.LocalDateTime;

public record DocumentByCategoryResponse(
        Long documentId,
        String title,
        String category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DocumentByCategoryResponse from(Document document) {
        return new DocumentByCategoryResponse(
                document.getId(),
                document.getTitle(),
                document.getCategory().getName(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
