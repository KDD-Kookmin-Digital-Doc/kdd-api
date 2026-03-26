package com.kdd.domain.document.dto;

import com.kdd.domain.document.entity.Document;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentStatusResponse {
    private Long id;
    private String title;
    private String status;

    public static DocumentStatusResponse from(Document document) {
        return DocumentStatusResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .status(document.getStatus().name())
                .build();
    }
}
