package com.kdd.domain.chat.repository;

import com.kdd.domain.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
