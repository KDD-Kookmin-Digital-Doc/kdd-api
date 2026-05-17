package com.kdd.chat.repository;

import com.kdd.chat.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Page<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<ChatSession> findByUserIdAndTitleContainingOrderByCreatedAtDesc(Long userId, String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"messages"})
    Optional<ChatSession> findWithMessagesById(Long id);

    // in-flight 락을 잡기 전에 ownership만 빠르게 확인하기 위한 projection — 전체 엔티티 fetch를 피한다.
    @Query("select s.user.id from ChatSession s where s.id = :sessionId")
    Optional<Long> findUserIdById(@Param("sessionId") Long sessionId);
}
