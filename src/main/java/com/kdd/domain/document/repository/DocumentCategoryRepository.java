package com.kdd.domain.document.repository;

import com.kdd.domain.document.entity.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {

    List<DocumentCategory> findByParentIsNullOrderBySortOrderAsc();
}
