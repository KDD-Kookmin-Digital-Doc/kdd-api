package com.kdd.faq.dto;

import com.kdd.chat.entity.ChatMessage;
import com.kdd.chat.entity.ChatSession;
import com.kdd.chat.entity.MessageRole;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * FAQ 기반 채팅 시작 응답.
 * FAQ 질문/답변을 초기 user/assistant 메시지로 가진 새 세션을 반환한다.
 * 메시지 응답 형태(sources/confidence 포함)는 기존 채팅 세션 상세 조회 응답과 일치시켜
 * FE가 동일한 메시지 렌더링 코드를 재사용할 수 있도록 한다. FAQ 답변은 RAG 결과가 없으므로
 * sources는 빈 배열, confidence는 null로 응답한다.
 */
public record FaqChatStartResponse(
        Long sessionId,
        String title,
        String sourceType,
        List<Message> messages
) {
    /**
     * 메시지 입력 순서가 뒤바뀌어도 응답은 항상 user → assistant 순서가 되도록 정렬한다.
     * (둘 다 ChatMessage 타입이라 인자 swap을 컴파일러가 막아주지 못하기 때문)
     */
    public static FaqChatStartResponse of(ChatSession session, List<ChatMessage> messages) {
        List<Message> ordered = messages.stream()
                .sorted(Comparator.comparing(m -> m.getRole() == MessageRole.USER ? 0 : 1))
                .map(Message::from)
                .toList();
        return new FaqChatStartResponse(
                session.getId(),
                session.getTitle(),
                session.getSourceType().getValue(),
                ordered
        );
    }

    public record Message(
            Long messageId,
            String role,
            String content,
            List<Object> sources,
            String confidence,
            LocalDateTime createdAt
    ) {
        public static Message from(ChatMessage message) {
            return new Message(
                    message.getId(),
                    message.getRole().getValue(),
                    message.getContent(),
                    List.of(),
                    message.getConfidenceLevel() != null ? message.getConfidenceLevel().getValue() : null,
                    message.getCreatedAt()
            );
        }
    }
}
