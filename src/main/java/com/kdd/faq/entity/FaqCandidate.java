package com.kdd.faq.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * FAQ 후보 도메인.
 * <p>
 * BE 스케줄러가 AI 서버 {@code POST /api/faq/analyze} 응답(인기 질문 TOP 5)을 받아 후보로 등록한다.
 * 관리자는 카테고리를 지정해 승인(→ FAQ 변환)하거나 반려할 수 있다.
 * <p>
 * 상태 전이: {@code PENDING → APPROVED} 또는 {@code PENDING → REJECTED}. 한 번 종료된 후보는 재전이 불가.
 */
@Entity
@Table(name = "faq_candidates", indexes = {
        @Index(name = "idx_faq_candidate_status_created", columnList = "status, created_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "draft_answer", nullable = false, columnDefinition = "TEXT")
    private String draftAnswer;

    // AI가 분류한 카테고리. 분류 실패 또는 미적용 시 null이 들어올 수 있어 nullable.
    // 승인 시점에 관리자가 최종 카테고리를 지정하므로 후보 단계에서 강제하지 않는다.
    @Convert(converter = FaqTopic.FaqTopicConverter.class)
    @Column(name = "category", length = 20)
    private FaqTopic category;

    // 인기 질문 클러스터링의 빈도수 (AI 서버 응답의 frequency 그대로 저장).
    @Column(nullable = false)
    private int frequency;

    @Convert(converter = FaqCandidateStatus.FaqCandidateStatusConverter.class)
    @Column(nullable = false, length = 20)
    private FaqCandidateStatus status;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public FaqCandidate(String question, String draftAnswer, FaqTopic category, int frequency) {
        this.question = question;
        this.draftAnswer = draftAnswer;
        this.category = category;
        this.frequency = frequency;
        this.status = FaqCandidateStatus.PENDING;
    }

    /**
     * PENDING 상태에서만 승인 가능. 그 외 상태에서 호출 시 도메인 invariant 위반으로 예외.
     * 카테고리는 관리자가 지정한 값(non-null)이어야 한다.
     */
    public void approve(FaqTopic chosenCategory, LocalDateTime now) {
        if (this.status != FaqCandidateStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 FAQ 후보는 다시 승인할 수 없습니다.");
        }
        if (chosenCategory == null) {
            throw new IllegalArgumentException("승인 시 카테고리는 필수입니다.");
        }
        this.category = chosenCategory;
        this.status = FaqCandidateStatus.APPROVED;
        this.approvedAt = now;
    }

    /**
     * PENDING 상태에서만 반려 가능. 그 외 상태에서 호출 시 도메인 invariant 위반으로 예외.
     */
    public void reject(LocalDateTime now) {
        if (this.status != FaqCandidateStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 FAQ 후보는 다시 반려할 수 없습니다.");
        }
        this.status = FaqCandidateStatus.REJECTED;
        this.rejectedAt = now;
    }
}
