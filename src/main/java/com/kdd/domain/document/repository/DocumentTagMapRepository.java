package com.kdd.domain.document.repository;

import com.kdd.domain.document.entity.DocumentTagMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentTagMapRepository extends JpaRepository<DocumentTagMap, Long> {

    List<DocumentTagMap> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);
}
