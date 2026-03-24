package com.kdd.domain.document.entity;

import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_documents_original_url", columnNames = "original_url")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentSource source;

    private String originalUrl;

    private String author;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private int viewCount;

    @Column(nullable = false)
    private boolean isNotice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Builder
    public Document(String title, String content, String summary,
                    String category, DocumentSource source,
                    String originalUrl, String author, LocalDateTime publishedAt,
                    DocumentStatus status) {
        this.title = title;
        this.content = content;
        this.summary = summary;
        this.category = category;
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        this.source = source;
        this.originalUrl = originalUrl;
        this.author = author;
        this.publishedAt = publishedAt;
        this.viewCount = 0;
        this.isNotice = false;
        this.status = status != null ? status : DocumentStatus.PENDING;
    }

    public void updateStatus(DocumentStatus status) {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        this.status = status;
    }
}
