package com.kdd.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_message_session_id", columnList = "session_id"),
        @Index(name = "idx_chat_message_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "confidence_level", length = 20)
    private ConfidenceLevel confidenceLevel;

    // SSE 스트리밍 시점엔 AI가 보낸 순서대로 INSERT되지만, GET /chat/sessions/{id}/messages로
    // 재조회할 때는 JPA가 fetch 순서를 보장하지 않는다. 저장 순서(= AI 송신 순서) 보존을 위해 id ASC로 명시.
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    private List<ChatMessageSource> sources = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ChatMessage(ChatSession session, MessageRole role, String content, ConfidenceLevel confidenceLevel) {
        this.session = session;
        this.role = role;
        this.content = content;
        this.confidenceLevel = confidenceLevel;
    }
}
