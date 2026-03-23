package com.kdd.domain.chat.repository;

import com.kdd.domain.chat.entity.ChatMessageSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageSourceRepository extends JpaRepository<ChatMessageSource, Long> {

    List<ChatMessageSource> findByMessageId(Long messageId);
}
