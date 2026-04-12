package com.kdd.chat.entity;

import com.kdd.document.entity.Document;
import com.kdd.document.entity.DocumentChunk;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "chat_message_sources", indexes = {
        @Index(name = "idx_chat_msg_src_message_id", columnList = "message_id"),
        @Index(name = "idx_chat_msg_src_document_id", columnList = "document_id"),
        @Index(name = "idx_chat_msg_src_chunk_id", columnList = "document_chunk_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_message_chunk", columnNames = {"message_id", "document_chunk_id"})
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
    @BatchSize(size = 50)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_chunk_id", nullable = false)
    private DocumentChunk documentChunk;

    @Column(name = "chunk_text", columnDefinition = "TEXT")
    private String chunkText;

    @Column
    private Integer page;

    @Builder
    public ChatMessageSource(ChatMessage message, Document document, DocumentChunk documentChunk,
                             String chunkText, Integer page) {
        this.message = message;
        this.document = document;
        this.documentChunk = documentChunk;
        this.chunkText = chunkText;
        this.page = page;
    }
}
