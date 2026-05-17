package com.kdd.chat.service;

import com.kdd.chat.dto.*;
import com.kdd.chat.entity.ChatSession;
import com.kdd.chat.entity.SourceType;
import com.kdd.chat.repository.ChatSessionRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.global.response.MessageResponse;
import com.kdd.global.response.PageResponse;
import com.kdd.user.entity.User;
import com.kdd.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;

    // 세션 생성 직후 잠깐 노출되는 placeholder. 첫 질문이 전송되면 프론트가 첫 15글자로 PATCH 덮어쓴다.
    private static final String DEFAULT_TITLE = "새 채팅";
    private static final int MAX_PAGE_SIZE = 100;

    @Transactional
    public ChatSessionCreateResponse createSession(Long userId, String requestedSourceType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // FE가 sourceType을 보내지 않거나 알 수 없는 값이면 NORMAL로 폴백 — 일반 채팅 진입 호환성 유지.
        // 알 수 없는 값에 대해 400을 던지면 FE 회귀로 채팅 자체가 막혀버리므로 보수적으로 fallback 한다.
        SourceType resolved = resolveSourceType(requestedSourceType);

        ChatSession session = ChatSession.builder()
                .user(user)
                .title(DEFAULT_TITLE)
                .sourceType(resolved)
                .build();
        chatSessionRepository.save(session);

        return ChatSessionCreateResponse.from(session);
    }

    private SourceType resolveSourceType(String value) {
        if (value == null || value.isBlank()) {
            return SourceType.NORMAL;
        }
        try {
            return SourceType.from(value);
        } catch (IllegalArgumentException e) {
            return SourceType.NORMAL;
        }
    }

    public PageResponse<ChatSessionListResponse> getSessions(Long userId, String keyword, int page, int pageSize) {
        validatePageParams(page, pageSize);

        if (keyword != null && !keyword.isBlank()) {
            return PageResponse.from(
                    chatSessionRepository.findByUserIdAndTitleContainingOrderByCreatedAtDesc(
                            userId, keyword, PageRequest.of(page, pageSize)),
                    ChatSessionListResponse::from
            );
        }

        return PageResponse.from(
                chatSessionRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, pageSize)),
                ChatSessionListResponse::from
        );
    }

    public ChatSessionDetailResponse getSessionDetail(Long sessionId, Long userId) {
        ChatSession session = findSessionOrThrow(sessionId);
        validateOwnership(session, userId);

        ChatSession detailedSession = chatSessionRepository.findWithMessagesById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        return ChatSessionDetailResponse.from(detailedSession);
    }

    @Transactional
    public ChatSessionUpdateResponse updateSession(Long sessionId, Long userId, String title) {
        ChatSession session = findSessionOrThrow(sessionId);
        validateOwnership(session, userId);
        session.updateTitle(title);
        return ChatSessionUpdateResponse.from(session);
    }

    @Transactional
    public MessageResponse deleteSession(Long sessionId, Long userId) {
        ChatSession session = findSessionOrThrow(sessionId);
        validateOwnership(session, userId);
        chatSessionRepository.delete(session);
        return new MessageResponse("채팅 세션이 삭제되었습니다.");
    }

    private ChatSession findSessionOrThrow(Long sessionId) {
        return chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    private void validateOwnership(ChatSession session, Long userId) {
        if (!session.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_FORBIDDEN);
        }
    }

    private void validatePageParams(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
