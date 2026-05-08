package com.kdd.document.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_doc_category", columnList = "category_id"),
        @Index(name = "idx_doc_title", columnList = "title"),
        @Index(name = "idx_doc_status", columnList = "status"),
        @Index(name = "idx_doc_published_at", columnList = "published_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

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

    @Convert(converter = DocumentSource.DocumentSourceConverter.class)
    @Column(nullable = false, length = 20)
    private DocumentSource source;

    @Column(name = "original_url", length = 1000)
    private String originalUrl;

    @Column(length = 100)
    private String author;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Convert(converter = DocumentStatus.DocumentStatusConverter.class)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Document(String title, String content, DocumentCategory category,
                    DocumentSource source, String originalFilename, String storageKey,
                    String mimeType, Long fileSize, DocumentStatus status) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.source = source;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.status = (status == null) ? DocumentStatus.UPLOADED : status;
        this.viewCount = 0;
    }

    public void updateCategory(DocumentCategory category) {
        this.category = category;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void updateStatus(DocumentStatus status) {
        this.status = status;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
