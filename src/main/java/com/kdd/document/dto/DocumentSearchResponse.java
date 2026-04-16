package com.kdd.document.dto;

import com.kdd.document.entity.Document;

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
}
