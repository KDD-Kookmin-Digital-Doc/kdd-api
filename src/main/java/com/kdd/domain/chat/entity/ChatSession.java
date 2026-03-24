package com.kdd.domain.chat.entity;

import com.kdd.domain.user.entity.User;
import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatSourceType sourceType;

    @Builder
    public ChatSession(User user, String title, ChatSourceType sourceType) {
        this.user = user;
        this.title = title;
        this.sourceType = sourceType != null ? sourceType : ChatSourceType.NORMAL;
    }
}
