package com.kdd.document.dto;

import com.kdd.document.entity.Document;

import java.time.LocalDateTime;

public record DocumentListResponse(
        Long id,
        String title,
        Long categoryId,
        String categoryName,
        String status,
        String source,
        LocalDateTime createdAt
) {
    public static DocumentListResponse from(Document document) {
        return new DocumentListResponse(
                document.getId(),
                document.getTitle(),
                document.getCategory().getId(),
                document.getCategory().getName(),
                document.getStatus().getValue(),
                document.getSource().name(),
                document.getCreatedAt()
        );
    }
}
