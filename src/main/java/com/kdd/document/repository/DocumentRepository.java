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

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT d FROM Document d WHERE d.category.id IN :categoryIds AND d.deletedAt IS NULL")
    Page<Document> findByCategoryIds(@Param("categoryIds") List<Long> categoryIds, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    @Query("""
            SELECT d FROM Document d
            WHERE d.deletedAt IS NULL
              AND (:hasCategoryFilter = false OR d.category.id IN :categoryIds)
              AND (:keyword IS NULL OR d.title LIKE CONCAT('%', :keyword, '%') ESCAPE '\\')
            """)
    Page<Document> searchActive(@Param("hasCategoryFilter") boolean hasCategoryFilter,
                                @Param("categoryIds") List<Long> categoryIds,
                                @Param("keyword") String keyword,
                                Pageable pageable);

    @Query(value = """
            SELECT d.id, d.title, dc.name AS category_name,
                   d.view_count,
                   COALESCE(ref.cnt, 0) AS reference_count,
                   (d.view_count + COALESCE(ref.cnt, 0)) AS popularity_score,
                   d.updated_at
            FROM documents d
            JOIN document_categories dc ON dc.id = d.category_id
            LEFT JOIN (
                SELECT cms.document_id, COUNT(DISTINCT cms.message_id) AS cnt
                FROM chat_message_sources cms
                JOIN chat_messages cm ON cm.id = cms.message_id
                WHERE cm.created_at >= :since
                GROUP BY cms.document_id
            ) ref ON ref.document_id = d.id
            WHERE d.deleted_at IS NULL
            ORDER BY popularity_score DESC, d.id DESC
            """, nativeQuery = true)
    List<PopularDocumentProjection> findPopularDocuments(@Param("since") LocalDateTime since, Pageable pageable);

    @Query(value = """
            SELECT d.id, d.title, dc.name AS category_name,
                   d.created_at, d.updated_at,
                   (d.view_count + COALESCE(ref.cnt, 0)) AS popularity_score
            FROM documents d
            JOIN document_categories dc ON dc.id = d.category_id
            LEFT JOIN (
                SELECT cms.document_id, COUNT(DISTINCT cms.message_id) AS cnt
                FROM chat_message_sources cms
                JOIN chat_messages cm ON cm.id = cms.message_id
                WHERE cm.created_at >= :since
                GROUP BY cms.document_id
            ) ref ON ref.document_id = d.id
            WHERE d.deleted_at IS NULL
              AND (:hasCategoryFilter = false OR d.category_id IN (:categoryIds))
              AND (:keyword IS NULL OR d.title LIKE CONCAT('%%', :keyword, '%%') ESCAPE '\\')
            ORDER BY popularity_score DESC, d.updated_at DESC, d.id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM documents d
            WHERE d.deleted_at IS NULL
              AND (:hasCategoryFilter = false OR d.category_id IN (:categoryIds))
              AND (:keyword IS NULL OR d.title LIKE CONCAT('%%', :keyword, '%%') ESCAPE '\\')
            """,
            nativeQuery = true)
    Page<SearchByPopularityProjection> searchActiveByPopularity(
            @Param("since") LocalDateTime since,
            @Param("hasCategoryFilter") boolean hasCategoryFilter,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Document d SET d.viewCount = d.viewCount + 1 WHERE d.id = :id AND d.deletedAt IS NULL")
    void incrementViewCount(@Param("id") Long id);
}
