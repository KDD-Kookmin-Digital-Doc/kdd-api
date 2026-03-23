package com.kdd.domain.document.repository;

import com.kdd.domain.document.entity.DocumentTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentTagRepository extends JpaRepository<DocumentTag, Long> {

    Optional<DocumentTag> findByCode(String code);
}
