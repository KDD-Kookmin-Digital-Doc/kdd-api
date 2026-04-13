package com.kdd.document.repository;

import com.kdd.document.entity.DocumentChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    @Modifying
    void deleteByDocumentId(Long documentId);

    @EntityGraph(attributePaths = {"document", "document.category"})
    @Query("SELECT c FROM DocumentChunk c WHERE c.document.deletedAt IS NULL ORDER BY c.id ASC")
    List<DocumentChunk> findAllActiveWithDocument();

    @EntityGraph(attributePaths = {"document", "document.category"})
    @Query(value = "SELECT c FROM DocumentChunk c WHERE c.document.deletedAt IS NULL",
            countQuery = "SELECT COUNT(c) FROM DocumentChunk c WHERE c.document.deletedAt IS NULL")
    Page<DocumentChunk> findAllActivePaged(Pageable pageable);

    @EntityGraph(attributePaths = {"document", "document.category"})
    @Query(value = "SELECT c FROM DocumentChunk c WHERE c.document.deletedAt IS NULL AND c.document.originalFilename = :docName",
            countQuery = "SELECT COUNT(c) FROM DocumentChunk c WHERE c.document.deletedAt IS NULL AND c.document.originalFilename = :docName")
    Page<DocumentChunk> findByDocNamePaged(@Param("docName") String docName, Pageable pageable);

    @EntityGraph(attributePaths = {"document", "document.category"})
    @Query("SELECT c FROM DocumentChunk c WHERE c.id = :id AND c.document.deletedAt IS NULL")
    Optional<DocumentChunk> findByIdActive(@Param("id") Long id);

    @EntityGraph(attributePaths = {"document", "document.category"})
    @Query("SELECT c FROM DocumentChunk c WHERE c.document.deletedAt IS NULL AND c.document.originalFilename = :docName ORDER BY c.id ASC")
    List<DocumentChunk> findByDocName(@Param("docName") String docName);

    @Query("SELECT c.document.originalFilename, COUNT(c) FROM DocumentChunk c " +
            "WHERE c.document.deletedAt IS NULL " +
            "GROUP BY c.document.originalFilename " +
            "ORDER BY COUNT(c) DESC")
    List<Object[]> countGroupByDocName();

    @Query("SELECT COUNT(DISTINCT c.document.originalFilename) FROM DocumentChunk c WHERE c.document.deletedAt IS NULL")
    long countDistinctDocNames();

    @Query("SELECT COUNT(c) FROM DocumentChunk c WHERE c.document.deletedAt IS NULL")
    long countActive();
}
