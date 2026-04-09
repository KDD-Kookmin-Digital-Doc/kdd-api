package com.kdd.document.repository;

import com.kdd.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT d FROM Document d WHERE d.deletedAt IS NULL")
    Page<Document> findAllActive(Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.deletedAt IS NULL")
    Optional<Document> findActiveById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT d FROM Document d WHERE d.category.id IN :categoryIds AND d.deletedAt IS NULL")
    Page<Document> findByCategoryIds(@Param("categoryIds") List<Long> categoryIds, Pageable pageable);
}
