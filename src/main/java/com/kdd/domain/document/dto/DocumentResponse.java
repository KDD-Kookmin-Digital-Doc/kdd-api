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
    private Long categoryId;
    private String categoryName;
    private String source;
    private String status;
    private String originalUrl;
    private String originalFilename;
    private String mimeType;
    private Long fileSize;
    private String author;
    private LocalDateTime createdAt;

    public static DocumentResponse from(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .categoryId(document.getCategory() != null ? document.getCategory().getId() : null)
                .categoryName(document.getCategory() != null ? document.getCategory().getName() : null)
                .source(document.getSource().name())
                .status(document.getStatus().name())
                .originalUrl(document.getOriginalUrl())
                .originalFilename(document.getOriginalFilename())
                .mimeType(document.getMimeType())
                .fileSize(document.getFileSize())
                .author(document.getAuthor())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
