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
        // fileUrl은 스트리밍 엔드포인트 경로. 원본 저장 경로(storageKey)는 내부 구현이라 노출하지 않음
        String fileUrl = document.getStorageKey() != null && !document.getStorageKey().isBlank()
                ? "/documents/" + document.getId() + "/file"
                : null;
        return new DocumentDetailPublicResponse(
                document.getId(),
                document.getTitle(),
                document.getCategory().getName(),
                fileUrl,
                document.getViewCount(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
