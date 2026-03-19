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

    @Query("SELECT d.docName, COUNT(d) FROM DocumentChunk d " +
           "WHERE d.docName LIKE '[공지]%' OR d.docName LIKE '[공지첨부]%' " +
           "GROUP BY d.docName ORDER BY d.id DESC")
    List<Object[]> findNoticeDocs();
}
