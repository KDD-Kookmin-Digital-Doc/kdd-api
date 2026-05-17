package com.kdd.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 인기 문서 7일 윈도우 점수 산정용 view tracking row.
 * <p>
 * 정책:
 * <ul>
 *     <li>인증된 사용자의 GET /documents/{id} 호출마다 1개 insert (출처 링크 클릭도 동일 경로)</li>
 *     <li>인기 점수는 7일 윈도우 COUNT DISTINCT user_id — 한 명이 100번 진입해도 1로 카운트</li>
 *     <li>documents.view_count(누적)와 분리된 별도 추적 — 누적값은 문서 상세 응답의 viewCount 표시에 계속 사용</li>
 * </ul>
 * Document·User association은 단순 카운트 집계엔 불필요하므로 Long FK만 보관해 LAZY 프록시 비용 회피.
 */
@Entity
@Table(name = "document_views", indexes = {
        @Index(name = "idx_document_views_document_viewed_at", columnList = "document_id, viewed_at"),
        @Index(name = "idx_document_views_user_viewed_at", columnList = "user_id, viewed_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(name = "viewed_at", nullable = false, updatable = false)
    private LocalDateTime viewedAt;

    @Builder
    public DocumentView(Long documentId, Long userId) {
        this.documentId = documentId;
        this.userId = userId;
    }
}
