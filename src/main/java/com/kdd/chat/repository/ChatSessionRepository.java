package com.kdd.chat.repository;

import com.kdd.chat.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Page<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<ChatSession> findByUserIdAndTitleContainingOrderByCreatedAtDesc(Long userId, String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"messages"})
    Optional<ChatSession> findWithMessagesById(Long id);
}
