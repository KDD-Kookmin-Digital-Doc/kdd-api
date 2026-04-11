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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter TITLE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Transactional
    public ChatSessionCreateResponse createSession(Long userId) {
        User user = userRepository.getReferenceById(userId);
        String title = LocalDateTime.now().format(TITLE_FORMATTER);

        ChatSession session = ChatSession.builder()
                .user(user)
                .title(title)
                .sourceType(SourceType.NORMAL)
                .build();
        chatSessionRepository.save(session);

        return ChatSessionCreateResponse.from(session);
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
        ChatSession session = chatSessionRepository.findWithMessagesById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        validateOwnership(session, userId);
        return ChatSessionDetailResponse.from(session);
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
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
