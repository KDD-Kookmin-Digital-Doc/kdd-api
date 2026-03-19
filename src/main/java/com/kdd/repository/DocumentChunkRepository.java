package com.kdd.repository;

import com.kdd.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, String> {

    List<DocumentChunk> findByDocName(String docName);

    void deleteByDocName(String docName);

    @Query("SELECT d.docName, COUNT(d) FROM DocumentChunk d GROUP BY d.docName")
    List<Object[]> countByDocNameGrouped();
}
