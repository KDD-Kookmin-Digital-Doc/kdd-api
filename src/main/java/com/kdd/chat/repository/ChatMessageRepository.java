package com.kdd.chat.repository;

import com.kdd.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // AI 호출 히스토리용. partial=true (스트림 중단으로 잘린 답변, #55) 는 제외해 AI 가 자기 잘린 출력을
    // 컨텍스트로 다시 받지 않게 한다 (#86). UI 표시용 히스토리는 partial 을 그대로 포함해야 하므로 별도 메서드.
    List<ChatMessage> findTop10BySessionIdAndPartialFalseOrderByCreatedAtDescIdDesc(Long sessionId);

    boolean existsBySessionId(Long sessionId);
}
