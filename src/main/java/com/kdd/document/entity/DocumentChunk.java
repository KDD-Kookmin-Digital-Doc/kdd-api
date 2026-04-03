package com.kdd.document.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_chunks", indexes = {
        @Index(name = "idx_chunk_document", columnList = "document_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_chunk_doc_index", columnNames = {"document_id", "chunk_index"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    private Integer page;

    @Column(name = "section_path", length = 500)
    private String sectionPath;

    @Column(name = "has_table", nullable = false)
    private boolean hasTable;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public DocumentChunk(Document document, String content, int chunkIndex,
                         Integer page, String sectionPath, boolean hasTable) {
        this.document = document;
        this.content = content;
        this.chunkIndex = chunkIndex;
        this.page = page;
        this.sectionPath = sectionPath;
        this.hasTable = hasTable;
    }
}
