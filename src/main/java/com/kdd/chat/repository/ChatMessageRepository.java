package com.kdd.chat.repository;

import com.kdd.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);

    long countBySessionId(Long sessionId);
}
