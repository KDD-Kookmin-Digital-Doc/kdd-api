package com.kdd.document.dto;

import com.kdd.document.entity.Document;

public record DocumentReprocessResponse(
        Long documentId,
        String status
) {
    public static DocumentReprocessResponse from(Document document) {
        return new DocumentReprocessResponse(
                document.getId(),
                document.getStatus().getValue()
        );
    }
}
