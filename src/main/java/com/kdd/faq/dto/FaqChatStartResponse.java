package com.kdd.faq.dto;

import com.kdd.chat.dto.ChatSessionDetailResponse.ChatMessageSourceResponse;
import com.kdd.chat.entity.ChatMessage;
import com.kdd.chat.entity.ChatSession;
import com.kdd.chat.entity.MessageRole;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * FAQ 기반 채팅 시작 응답. 노션 API 명세 "FAQ 기반 채팅 시작"을 그대로 따른다.
 * <p>
 * user 메시지는 messageId/role/content/createdAt만,
 * assistant 메시지는 RAG 응답과 동일하게 sources/confidence까지 포함한다.
 * FAQ 답변은 RAG 결과가 없으므로 sources는 빈 배열, confidence는 null.
 * sources 타입은 ChatSessionDetailResponse와 동일한 ChatMessageSourceResponse를 재사용해
 * FE가 두 응답을 동일한 메시지 렌더링 코드로 처리할 수 있도록 한다.
 */
public record FaqChatStartResponse(
        Long sessionId,
        List<Message> messages
) {
    public static FaqChatStartResponse of(ChatSession session, List<ChatMessage> messages) {
        // 입력 순서가 뒤바뀌어도 응답은 항상 user → assistant 순서가 되도록 정렬한다.
        List<Message> ordered = messages.stream()
                .sorted(Comparator.comparing(m -> m.getRole() == MessageRole.USER ? 0 : 1))
                .map(Message::from)
                .toList();
        return new FaqChatStartResponse(session.getId(), ordered);
    }

    /**
     * user/assistant 응답 필드가 달라 sealed로 분리.
     * Jackson은 각 인스턴스 타입에 맞춰 직렬화하므로 user에 sources/confidence가 노출되지 않는다.
     */
    public sealed interface Message permits UserMessage, AssistantMessage {
        static Message from(ChatMessage m) {
            return m.getRole() == MessageRole.USER
                    ? UserMessage.from(m)
                    : AssistantMessage.from(m);
        }
    }

    public record UserMessage(
            Long messageId,
            String role,
            String content,
            LocalDateTime createdAt
    ) implements Message {
        public static UserMessage from(ChatMessage m) {
            return new UserMessage(m.getId(), m.getRole().getValue(), m.getContent(), m.getCreatedAt());
        }
    }

    public record AssistantMessage(
            Long messageId,
            String role,
            String content,
            List<ChatMessageSourceResponse> sources,
            String confidence,
            LocalDateTime createdAt
    ) implements Message {
        public static AssistantMessage from(ChatMessage m) {
            return new AssistantMessage(
                    m.getId(),
                    m.getRole().getValue(),
                    m.getContent(),
                    List.of(),
                    m.getConfidenceLevel() != null ? m.getConfidenceLevel().getValue() : null,
                    m.getCreatedAt()
            );
        }
    }
}
