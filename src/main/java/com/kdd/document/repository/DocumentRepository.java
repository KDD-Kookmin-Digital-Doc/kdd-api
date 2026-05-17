package com.kdd.document.repository;

import com.kdd.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT d FROM Document d WHERE d.deletedAt IS NULL")
    Page<Document> findAllActive(Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.deletedAt IS NULL")
    Optional<Document> findActiveById(@Param("id") Long id);

    // 사용자 진입점 (카테고리 기반 조회) — COMPLETED 문서만 노출
    @EntityGraph(attributePaths = {"category"})
    @Query("""
            SELECT d FROM Document d
            WHERE d.category.id IN :categoryIds
              AND d.deletedAt IS NULL
              AND d.status = com.kdd.document.entity.DocumentStatus.COMPLETED
            """)
    Page<Document> findByCategoryIds(@Param("categoryIds") List<Long> categoryIds, Pageable pageable);

    // 사용자 진입점 (검색 latest) — COMPLETED 문서만 노출
    // keyword는 항상 non-null로 전달한다 (없을 땐 빈 문자열). Hibernate 6 + PostgreSQL JDBC가
    // 타입 힌트 없는 null String 파라미터를 bytea로 추론해 LIKE 절이 깨지는 회귀를 피하기 위함 (#72).
    @EntityGraph(attributePaths = {"category"})
    @Query("""
            SELECT d FROM Document d
            WHERE d.deletedAt IS NULL
              AND d.status = com.kdd.document.entity.DocumentStatus.COMPLETED
              AND (:hasCategoryFilter = false OR d.category.id IN :categoryIds)
              AND (:hasKeyword = false OR d.title LIKE CONCAT('%', :keyword, '%') ESCAPE '\\')
            """)
    Page<Document> searchActive(@Param("hasCategoryFilter") boolean hasCategoryFilter,
                                @Param("categoryIds") List<Long> categoryIds,
                                @Param("hasKeyword") boolean hasKeyword,
                                @Param("keyword") String keyword,
                                Pageable pageable);

    // 사용자 진입점 (인기 문서) — COMPLETED 문서만 노출. 요구사항 3-(2)-5 정책을 그대로 구현:
    //
    //   1) view_count : document_views 7일 윈도우 COUNT(DISTINCT user_id)
    //                   동일 사용자+문서 10분 내 중복 진입은 ChatMessagePersister/getDocumentDetail 측에서 제거하므로
    //                   여기서는 user_id 기준 distinct만으로 충분.
    //   2) reference_count: chat_message_sources 7일 윈도우 COUNT(DISTINCT message_id)
    //                      assistant 메시지 단위 unique 카운트 (한 응답에 같은 doc 여러 chunk 와도 1로 처리).
    //                      role='assistant' 필터를 명시 — 현재 RAG 파이프라인은 user 메시지에 source를 붙이지
    //                      않지만, 향후 user 첨부파일 source 같은 회귀 도입 시 인기점수가 두 배로 부풀어 오르는
    //                      drift를 방어한다 (StatisticsRepository.aggregateQuestionsByCategory와 동일 정책).
    //
    // popularity_score = view + reference (1:1 가중치).
    //
    // 관리자/테스트 계정 제외: 명세 "관리자 및 테스트 계정의 이벤트는 집계에서 제외"를 user.role = 'admin'
    // 기준으로 적용. 테스트 계정 분리 컬럼이 없어 admin만 1차 제외 — 테스트 계정 식별 컬럼 도입은 후속 작업.
    @Query(value = """
            SELECT d.id, d.title, dc.name AS category_name,
                   COALESCE(view_stats.cnt, 0) AS view_count,
                   COALESCE(ref.cnt, 0) AS reference_count,
                   (COALESCE(view_stats.cnt, 0) + COALESCE(ref.cnt, 0)) AS popularity_score,
                   d.updated_at
            FROM documents d
            JOIN document_categories dc ON dc.id = d.category_id
            LEFT JOIN (
                SELECT dv.document_id, COUNT(DISTINCT dv.user_id) AS cnt
                FROM document_views dv
                JOIN users u ON u.id = dv.user_id
                WHERE dv.viewed_at >= :since
                  AND u.role <> 'admin'
                GROUP BY dv.document_id
            ) view_stats ON view_stats.document_id = d.id
            LEFT JOIN (
                SELECT cms.document_id, COUNT(DISTINCT cms.message_id) AS cnt
                FROM chat_message_sources cms
                JOIN chat_messages cm ON cm.id = cms.message_id AND cm.role = 'assistant'
                JOIN chat_sessions cs ON cs.id = cm.session_id
                JOIN users u ON u.id = cs.user_id
                WHERE cm.created_at >= :since
                  AND u.role <> 'admin'
                GROUP BY cms.document_id
            ) ref ON ref.document_id = d.id
            WHERE d.deleted_at IS NULL
              AND d.status = 'completed'
            ORDER BY popularity_score DESC, d.id DESC
            """, nativeQuery = true)
    List<PopularDocumentProjection> findPopularDocuments(@Param("since") LocalDateTime since, Pageable pageable);

    // 사용자 진입점 (검색 popular) — COMPLETED 문서만 노출.
    // popularity_score 산정 정책은 findPopularDocuments와 동일하게 통일: 7일 윈도우 view + reference.
    // keyword null 시 native LIKE 절에서 bytea 추론 회귀를 피하기 위해 hasKeyword 가드 사용 (#72).
    @Query(value = """
            SELECT d.id, d.title, dc.name AS category_name,
                   d.created_at, d.updated_at,
                   (COALESCE(view_stats.cnt, 0) + COALESCE(ref.cnt, 0)) AS popularity_score
            FROM documents d
            JOIN document_categories dc ON dc.id = d.category_id
            LEFT JOIN (
                SELECT dv.document_id, COUNT(DISTINCT dv.user_id) AS cnt
                FROM document_views dv
                JOIN users u ON u.id = dv.user_id
                WHERE dv.viewed_at >= :since
                  AND u.role <> 'admin'
                GROUP BY dv.document_id
            ) view_stats ON view_stats.document_id = d.id
            LEFT JOIN (
                SELECT cms.document_id, COUNT(DISTINCT cms.message_id) AS cnt
                FROM chat_message_sources cms
                JOIN chat_messages cm ON cm.id = cms.message_id AND cm.role = 'assistant'
                JOIN chat_sessions cs ON cs.id = cm.session_id
                JOIN users u ON u.id = cs.user_id
                WHERE cm.created_at >= :since
                  AND u.role <> 'admin'
                GROUP BY cms.document_id
            ) ref ON ref.document_id = d.id
            WHERE d.deleted_at IS NULL
              AND d.status = 'completed'
              AND (:hasCategoryFilter = false OR d.category_id IN (:categoryIds))
              AND (:hasKeyword = false OR d.title LIKE CONCAT('%', :keyword, '%') ESCAPE '\\')
            ORDER BY popularity_score DESC, d.updated_at DESC, d.id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM documents d
            WHERE d.deleted_at IS NULL
              AND d.status = 'completed'
              AND (:hasCategoryFilter = false OR d.category_id IN (:categoryIds))
              AND (:hasKeyword = false OR d.title LIKE CONCAT('%', :keyword, '%') ESCAPE '\\')
            """,
            nativeQuery = true)
    Page<SearchByPopularityProjection> searchActiveByPopularity(
            @Param("since") LocalDateTime since,
            @Param("hasCategoryFilter") boolean hasCategoryFilter,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("hasKeyword") boolean hasKeyword,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 사용자 진입점에서만 호출. status가 COMPLETED인 활성 문서일 때만 view_count를 +1 한다.
     * 검사 → 갱신 사이에 관리자 reprocess가 끼어들어도 race로 카운트가 새지 않도록 단일 UPDATE로 처리.
     * @return 영향받은 row 수 (0 = 대상 문서 없음 → 호출자가 404 처리)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Document d SET d.viewCount = d.viewCount + 1
            WHERE d.id = :id
              AND d.deletedAt IS NULL
              AND d.status = com.kdd.document.entity.DocumentStatus.COMPLETED
            """)
    int incrementViewCount(@Param("id") Long id);
}
