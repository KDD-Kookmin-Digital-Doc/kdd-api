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
 * FAQ 후보 도메인 — [BE] ERD FAQCandidate 정의를 그대로 따른다.
 * <p>
 * BE 스케줄러가 AI 서버 {@code POST /api/faq/analyze} 응답을 받아 후보로 등록한다.
 * 관리자는 카테고리를 지정해 승인(→ FAQ 변환)하거나 반려할 수 있다.
 * <p>
 * 상태 전이: {@code PENDING → APPROVED}(faqId 채워짐) 또는 {@code PENDING → REJECTED}. 한 번 종료된 후보는 재전이 불가.
 * AI 응답의 {@code frequency}(클러스터 빈도)는 V7 마이그레이션으로 추가된 컬럼에 저장하여
 * 관리자 검토 화면에서 승인 우선순위 판단 근거로 사용한다.
 */
@Entity
@Table(name = "faq_candidates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    // AI 답변 초안. AI 미적용 또는 응답 부재 시 null 가능 (ERD 정의 그대로).
    @Column(name = "answer_draft", columnDefinition = "TEXT")
    private String answerDraft;

    // AI가 분류한 카테고리. 분류 실패 또는 미적용 시 null. 관리자가 승인 시점에 최종 지정.
    @Convert(converter = FaqTopic.FaqTopicConverter.class)
    @Column(length = 20)
    private FaqTopic category;

    @Convert(converter = FaqCandidateStatus.FaqCandidateStatusConverter.class)
    @Column(nullable = false, length = 20)
    private FaqCandidateStatus status;

    // 승인 시 생성된 FAQ의 id. 반려/대기 상태에서는 null. ON DELETE SET NULL.
    @Column(name = "faq_id")
    private Long faqId;

    // AI 응답의 클러스터 빈도. 사용자 질문 N개가 이 후보 질문으로 클러스터링됐는지를 의미.
    // V7 마이그레이션으로 NOT NULL DEFAULT 0이라 기본값을 정수 0으로 설정.
    @Column(nullable = false)
    private int frequency;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public FaqCandidate(String question, String answerDraft, FaqTopic category, Integer frequency) {
        this.question = question;
        this.answerDraft = answerDraft;
        this.category = category;
        // AI 분석 응답에 frequency가 누락된 경우(이전 시드/수동 인입)도 NOT NULL 제약을 만족시키도록 0으로 정규화.
        this.frequency = (frequency == null || frequency < 0) ? 0 : frequency;
        this.status = FaqCandidateStatus.PENDING;
    }

    /**
     * PENDING 상태에서만 승인 가능. 그 외 상태에서 호출 시 도메인 invariant 위반.
     * 승인 시 관리자가 선택한 카테고리 + 생성된 FAQ id를 함께 박는다.
     */
    public void approve(FaqTopic chosenCategory, Long createdFaqId, LocalDateTime now) {
        if (this.status != FaqCandidateStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 FAQ 후보는 다시 승인할 수 없습니다.");
        }
        if (chosenCategory == null) {
            throw new IllegalArgumentException("승인 시 카테고리는 필수입니다.");
        }
        if (createdFaqId == null) {
            throw new IllegalArgumentException("승인 시 FAQ id는 필수입니다.");
        }
        this.category = chosenCategory;
        this.faqId = createdFaqId;
        this.status = FaqCandidateStatus.APPROVED;
        this.approvedAt = now;
    }

    /**
     * PENDING 상태에서만 반려 가능. 반려 시각은 ERD에 별도 컬럼이 없어 updated_at으로 추적한다.
     */
    public void reject() {
        if (this.status != FaqCandidateStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 FAQ 후보는 다시 반려할 수 없습니다.");
        }
        this.status = FaqCandidateStatus.REJECTED;
    }
}
