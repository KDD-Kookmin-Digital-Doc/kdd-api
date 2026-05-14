package com.kdd.faq.dto;

import com.kdd.chat.dto.ChatMessageResponse;
import com.kdd.chat.entity.ChatMessage;
import com.kdd.chat.entity.ChatSession;

import java.util.List;

/**
 * FAQ 기반 채팅 시작 응답.
 * <p>
 * 메시지 응답은 채팅 세션 상세 조회와 동일한 공용 record({@link ChatMessageResponse})를 사용해 FE가 같은
 * 메시지 렌더링 컴포넌트를 그대로 재사용할 수 있도록 한다. FAQ 답변은 RAG 결과가 없으므로 새로 영속화한
 * ChatMessage의 sources는 빈 컬렉션, confidenceLevel은 null이라 응답에서도 각각 빈 배열·null로 노출된다.
 * messages 순서가 그대로 응답 순서가 된다 — 호출자가 user→assistant 순으로 전달할 책임을 진다.
 */
public record FaqChatStartResponse(
        Long sessionId,
        List<ChatMessageResponse> messages
) {
    public static FaqChatStartResponse of(ChatSession session, List<ChatMessage> messages) {
        return new FaqChatStartResponse(
                session.getId(),
                messages.stream().map(ChatMessageResponse::from).toList()
        );
    }
}
