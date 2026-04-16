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

    @EntityGraph(attributePaths = {"category"})
    @Query("""
            SELECT d FROM Document d
            WHERE d.deletedAt IS NULL
              AND d.updatedAt >= :since
            ORDER BY d.viewCount DESC, d.updatedAt DESC, d.id DESC
            """)
    List<Document> findPopularSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Modifying
    @Query("UPDATE Document d SET d.viewCount = d.viewCount + 1 WHERE d.id = :id AND d.deletedAt IS NULL")
    void incrementViewCount(@Param("id") Long id);
}
