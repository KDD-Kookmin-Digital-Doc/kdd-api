package com.kdd.faq.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "faqs", indexes = {
        @Index(name = "idx_faq_category", columnList = "category")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    // API 필드명은 topic, DB 컬럼은 category로 매핑 (ERD 정의)
    @Convert(converter = FaqTopic.FaqTopicConverter.class)
    @Column(name = "category", nullable = false, length = 20)
    private FaqTopic topic;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Faq(String question, String answer, FaqTopic topic) {
        this.question = question;
        this.answer = answer;
        this.topic = topic;
    }

    /**
     * PATCH 부분 업데이트: 전달된 값(non-null)만 적용한다.
     * 빈 문자열 거부 정책은 Service 계층(rejectIfBlank)에서 일관 처리되며, 도메인은 단순한 null-skip에 집중한다.
     */
    public void update(String question, String answer, FaqTopic topic) {
        if (question != null) this.question = question;
        if (answer != null) this.answer = answer;
        if (topic != null) this.topic = topic;
    }
}
