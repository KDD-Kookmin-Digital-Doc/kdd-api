package com.kdd.faq.dto;

import com.kdd.chat.dto.ChatSessionDetailResponse.ChatMessageSourceResponse;
import com.kdd.chat.entity.ChatMessage;
import com.kdd.chat.entity.ChatSession;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FAQ 기반 채팅 시작 응답.
 * <p>
 * 메시지 응답 형태(role/sources/confidence)는 채팅 세션 상세 조회 응답과 동일한 단일 record로
 * 통일해 FE가 같은 메시지 렌더링 컴포넌트를 그대로 재사용할 수 있도록 한다.
 * FAQ 답변은 RAG 결과가 없으므로 assistant 메시지의 sources는 빈 배열, confidence는 null.
 * user 메시지도 동일 구조라 sources/confidence가 각각 빈 배열·null로 노출된다.
 */
public record FaqChatStartResponse(
        Long sessionId,
        List<Message> messages
) {
    /**
     * 호출자가 user → assistant 순서로 List를 전달하면 응답 순서도 그대로 유지된다.
     * 호출자 contract: messages 순서대로 FE에 노출되므로 service가 정확한 순서로 넘겨야 한다.
     */
    public static FaqChatStartResponse of(ChatSession session, List<ChatMessage> messages) {
        return new FaqChatStartResponse(
                session.getId(),
                messages.stream().map(Message::from).toList()
        );
    }

    public record Message(
            Long messageId,
            String role,
            String content,
            List<ChatMessageSourceResponse> sources,
            String confidence,
            LocalDateTime createdAt
    ) {
        public static Message from(ChatMessage m) {
            return new Message(
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
