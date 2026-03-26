package com.kdd.domain.faq.entity;

import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "faq_candidates", indexes = {
        @Index(columnList = "status"),
        @Index(columnList = "category"),
        @Index(columnList = "faq_id"),
        @Index(columnList = "recommended_question_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqCandidate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answerDraft;

    @Enumerated(EnumType.STRING)
    private FaqCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FaqCandidateStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faq_id")
    private Faq faq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommended_question_id")
    private RecommendedQuestion recommendedQuestion;

    private LocalDateTime approvedAt;

    @Builder
    public FaqCandidate(String question, String answerDraft, FaqCategory category,
                        FaqCandidateStatus status, RecommendedQuestion recommendedQuestion) {
        this.question = question;
        this.answerDraft = answerDraft;
        this.category = category;
        this.status = status != null ? status : FaqCandidateStatus.PENDING;
        this.recommendedQuestion = recommendedQuestion;
    }

    public void approve(Faq faq) {
        if (faq == null) throw new IllegalArgumentException("faq must not be null");
        this.status = FaqCandidateStatus.APPROVED;
        this.faq = faq;
        this.approvedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = FaqCandidateStatus.REJECTED;
        this.faq = null;
        this.approvedAt = null;
    }
}
