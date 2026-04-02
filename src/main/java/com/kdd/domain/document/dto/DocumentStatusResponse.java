package com.kdd.domain.document.dto;

import com.kdd.domain.document.entity.Document;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentStatusResponse {
    private Long documentId;
    private String status;

    public static DocumentStatusResponse from(Document document) {
        return DocumentStatusResponse.builder()
                .documentId(document.getId())
                .status(document.getStatus().name().toLowerCase())
                .build();
    }
}
