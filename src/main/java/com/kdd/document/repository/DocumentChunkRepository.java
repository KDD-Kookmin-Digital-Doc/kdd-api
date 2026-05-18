package com.kdd.document.repository;

import com.kdd.document.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    // derived delete 는 매칭 행을 SELECT 후 행마다 DELETE 를 발행하므로 큰 PDF (200+ 청크) 에서
    // 트랜잭션 비용이 선형 증가한다. ChatMessageSourceRepository.deleteByDocumentId (#45) 와 동일한
    // 패턴으로 bulk JPQL DELETE 를 명시한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DocumentChunk c WHERE c.document.id = :documentId")
    int deleteByDocumentId(@Param("documentId") Long documentId);

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);
}
