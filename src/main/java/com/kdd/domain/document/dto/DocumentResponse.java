package com.kdd.domain.document.dto;

import com.kdd.domain.document.entity.Document;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DocumentResponse {

    private Long id;
    private String title;
    private String category;
    private String source;
    private String status;
    private String originalUrl;
    private String author;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentResponse from(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .category(document.getCategory())
                .source(document.getSource().name())
                .status(document.getStatus().name())
                .originalUrl(document.getOriginalUrl())
                .author(document.getAuthor())
                .publishedAt(document.getPublishedAt())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
