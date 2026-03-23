package com.kdd.domain.chat.entity;

import com.kdd.domain.document.entity.Document;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_message_sources")
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

    @Column(columnDefinition = "TEXT")
    private String chunkText;

    private Integer page;

    @Builder
    public ChatMessageSource(ChatMessage message, Document document, String chunkText, Integer page) {
        this.message = message;
        this.document = document;
        this.chunkText = chunkText;
        this.page = page;
    }
}
