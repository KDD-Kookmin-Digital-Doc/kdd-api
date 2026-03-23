package com.kdd.domain.faq.entity;

import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "faq_candidates")
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

    @Column(nullable = false)
    private int frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FaqCandidateStatus status;

    private LocalDateTime approvedAt;

    @Builder
    public FaqCandidate(String question, String answerDraft, FaqCategory category,
                        int frequency, FaqCandidateStatus status) {
        this.question = question;
        this.answerDraft = answerDraft;
        this.category = category;
        this.frequency = frequency;
        this.status = status != null ? status : FaqCandidateStatus.PENDING;
    }

    public void approve() {
        this.status = FaqCandidateStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = FaqCandidateStatus.REJECTED;
    }
}
