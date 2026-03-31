package com.kdd.domain.document.dto;

import com.kdd.domain.document.entity.Document;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DocumentListResponse {

    private Long id;
    private String title;
    private Long categoryId;
    private String categoryName;
    private String status;
    private String source;
    private LocalDateTime createdAt;

    public static DocumentListResponse from(Document document) {
        return DocumentListResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .categoryId(document.getCategory() != null ? document.getCategory().getId() : null)
                .categoryName(document.getCategory() != null ? document.getCategory().getName() : null)
                .status(document.getStatus().name())
                .source(document.getSource().name())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
