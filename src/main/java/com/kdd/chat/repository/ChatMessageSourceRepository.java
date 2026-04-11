package com.kdd.chat.repository;

import com.kdd.chat.entity.ChatMessageSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageSourceRepository extends JpaRepository<ChatMessageSource, Long> {
}
