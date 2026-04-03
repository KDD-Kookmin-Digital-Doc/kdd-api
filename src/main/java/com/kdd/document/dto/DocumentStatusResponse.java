package com.kdd.document.dto;

import com.kdd.document.entity.Document;

import java.time.LocalDateTime;

public record DocumentStatusResponse(
        Long documentId,
        String status,
        LocalDateTime createdAt
) {
    public static DocumentStatusResponse from(Document document) {
        return new DocumentStatusResponse(
                document.getId(),
                document.getStatus().getValue(),
                document.getCreatedAt()
        );
    }
}
