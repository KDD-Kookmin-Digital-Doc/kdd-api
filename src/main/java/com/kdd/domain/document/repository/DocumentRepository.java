package com.kdd.domain.document.repository;

import com.kdd.domain.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByOrderByCreatedAtDesc();

    boolean existsByOriginalUrl(String originalUrl);

    @Query("SELECT d FROM Document d JOIN FETCH d.category WHERE d.deletedAt IS NULL ORDER BY d.createdAt DESC")
    Page<Document> findAllActive(Pageable pageable);
}
