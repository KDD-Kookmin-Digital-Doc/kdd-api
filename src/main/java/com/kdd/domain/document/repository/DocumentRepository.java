package com.kdd.domain.document.repository;

import com.kdd.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByOrderByCreatedAtDesc();
}
