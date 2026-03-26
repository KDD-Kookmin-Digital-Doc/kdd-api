package com.kdd.domain.chat.repository;

import com.kdd.domain.chat.entity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {
}
