package com.kdd.domain.chat.entity;

import com.kdd.domain.document.entity.Document;
import com.kdd.domain.document.entity.DocumentChunk;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_message_sources",
        uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "document_chunk_id"}),
        indexes = {
                @Index(columnList = "message_id"),
                @Index(columnList = "document_id"),
                @Index(columnList = "document_chunk_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_chunk_id", nullable = false)
    private DocumentChunk documentChunk;

    @Column(columnDefinition = "TEXT")
    private String chunkText;

    private Integer page;

    @Builder
    public ChatMessageSource(ChatMessage message, Document document, DocumentChunk documentChunk,
                             String chunkText, Integer page) {
        if (document != null && documentChunk != null
                && !document.getId().equals(documentChunk.getDocument().getId())) {
            throw new IllegalArgumentException("document와 documentChunk의 문서가 일치하지 않습니다.");
        }
        this.message = message;
        this.document = document;
        this.documentChunk = documentChunk;
        this.chunkText = chunkText;
        this.page = page;
    }
}
