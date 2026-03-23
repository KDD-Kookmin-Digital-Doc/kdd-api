package com.kdd.domain.document.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_chunks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int chunkIndex;

    private Integer page;

    private String sectionPath;

    @Column(nullable = false)
    private boolean hasTable;

    @CreatedDate
    @Column(updatable = false)
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
