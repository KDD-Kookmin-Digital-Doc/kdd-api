package com.kdd.document.repository;

import com.kdd.document.entity.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {

    @Query("SELECT c FROM DocumentCategory c ORDER BY c.depth ASC, c.sortOrder ASC")
    List<DocumentCategory> findAllOrdered();
}
