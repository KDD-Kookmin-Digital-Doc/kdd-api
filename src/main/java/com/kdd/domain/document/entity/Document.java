package com.kdd.domain.document.entity;

import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents", indexes = {
        @Index(columnList = "category_id"),
        @Index(columnList = "title"),
        @Index(columnList = "status"),
        @Index(columnList = "published_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private DocumentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentSource source;

    @Column(length = 1000)
    private String originalUrl;

    @Column(length = 100)
    private String author;

    private LocalDateTime publishedAt;

    @Column(length = 255)
    private String originalFilename;

    @Column(length = 500)
    private String storageKey;

    @Column(length = 100)
    private String mimeType;

    private Long fileSize;

    @Column(nullable = false)
    private int viewCount;

    @Column(nullable = false)
    private boolean isNotice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    private LocalDateTime deletedAt;

    @Builder
    public Document(String title, String content, String summary,
                    DocumentCategory category, DocumentSource source,
                    String originalUrl, String author, LocalDateTime publishedAt,
                    String originalFilename, String storageKey, String mimeType, Long fileSize,
                    DocumentStatus status) {
        this.title = title;
        this.content = content;
        this.summary = summary;
        this.category = category;
        this.source = source;
        this.originalUrl = originalUrl;
        this.author = author;
        this.publishedAt = publishedAt;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.viewCount = 0;
        this.isNotice = false;
        this.status = status != null ? status : DocumentStatus.PENDING;
    }

    public void updateStatus(DocumentStatus status) {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        this.status = status;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
