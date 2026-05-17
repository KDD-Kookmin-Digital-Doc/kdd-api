package com.kdd.faq.service;

import com.kdd.chat.entity.ChatMessage;
import com.kdd.chat.entity.ChatSession;
import com.kdd.chat.entity.MessageRole;
import com.kdd.chat.entity.SourceType;
import com.kdd.chat.repository.ChatMessageRepository;
import com.kdd.chat.repository.ChatSessionRepository;
import com.kdd.faq.dto.FaqChatStartResponse;
import com.kdd.faq.entity.Faq;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.user.entity.User;
import com.kdd.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FAQ → Chat 진입 오케스트레이션 전용 서비스.
 * <p>
 * FAQ 도메인이 Chat 도메인에 의존하는 단방향 흐름이므로 FaqService에 메서드를 추가하는 대신
 * 별도 서비스로 분리해 FaqService(순수 FAQ CRUD)의 책임을 좁게 유지한다.
 * 세션 제목 포맷은 일반 채팅 세션 생성과 동일한 규칙(yyyyMMdd_HHmmss)을 따른다.
 */
@Service
@RequiredArgsConstructor
public class FaqChatService {

    private static final DateTimeFormatter TITLE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final FaqService faqService;
    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * FAQ 질문/답변을 초기 메시지로 가지는 새 채팅 세션을 생성한다.
     * source_type='faq' 세션은 rate limit 정책상 일반 채팅과 무관하게 자동 생성되는 초기 메시지를
     * 카운트하지 않으므로(별도 호출 없이 트랜잭션 내에서만 저장), AI 호출이나 카운터 증가가 없다.
     *
     * @return 새 세션 ID와 [user, assistant] 순서의 초기 메시지 2건
     */
    @Transactional
    public FaqChatStartResponse startChat(Long faqId, Long userId) {
        Faq faq = faqService.findFaqOrThrow(faqId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .user(user)
                .title(LocalDateTime.now().format(TITLE_FORMATTER))
                .sourceType(SourceType.FAQ)
                .build());

        // user → assistant 순서대로 저장해 id 오름차순이 대화 순서가 되도록 한다.
        // (@CreationTimestamp가 같은 밀리초로 찍힐 가능성이 있어 id를 tie-breaker로 활용)
        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(faq.getQuestion())
                .build());
        ChatMessage assistantMessage = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.ASSISTANT)
                .content(faq.getAnswer())
                .build());

        return FaqChatStartResponse.of(session, List.of(userMessage, assistantMessage));
    }
}
