package com.kdd.chat.repository;

import com.kdd.chat.entity.ChatMessageSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageSourceRepository extends JpaRepository<ChatMessageSource, Long> {

    // chat_message_sources.document_chunk_id FK 가 ON DELETE CASCADE 가 아니라
    // 청크 hard delete 가 FK 위반으로 실패하므로, 같은 트랜잭션에서 청크 삭제 직전에
    // 해당 문서의 출처 행을 모두 비워 FK 위반을 막는다 (#45).
    // 삭제 기준은 document_id 이고, 그 결과로 같은 문서에 속한 모든 청크의 출처가 일괄 정리된다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatMessageSource cms WHERE cms.document.id = :documentId")
    int deleteByDocumentId(@Param("documentId") Long documentId);
}
