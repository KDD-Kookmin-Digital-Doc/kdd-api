package com.kdd.chat.service;

import com.kdd.chat.dto.ChatUsageResponse;
import com.kdd.chat.exception.RateLimitExceededException;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.user.entity.User;
import com.kdd.user.entity.UserChatUsage;
import com.kdd.user.repository.UserChatUsageRepository;
import com.kdd.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ChatRateLimitService {

    // 명세서 합의: 한도는 매일 자정(KST) 기준으로 초기화 → 일별 row(user_chat_usage)로 자동 처리
    private static final ZoneId RESET_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final UserChatUsageRepository userChatUsageRepository;

    /**
     * 채팅 메시지 전송 직전에 호출. 한도 초과면 {@link RateLimitExceededException}을 던지고,
     * 통과면 {@code chat_count}를 1 증가시켜 남은 횟수를 반환한다.
     *
     * 별도의 짧은 트랜잭션(REQUIRES_NEW)으로 묶어 SSE 스트리밍보다 먼저 커밋되도록 하고,
     * 동일 사용자의 동시 요청은 user_chat_usage row 잠금으로 순차화한다.
     *
     * 첫 진입 race는 {@code INSERT ... ON CONFLICT DO NOTHING}로 안전하게 처리한다 —
     * Hibernate save + catch 패턴은 PostgreSQL이 unique violation 시 트랜잭션 전체를
     * abort 상태로 만들기 때문에 catch 블록에서 후속 쿼리가 실패한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int checkAndIncrement(Long userId) {
        User user = findUser(userId);
        int limit = user.getDailyChatLimit();
        LocalDate today = LocalDate.now(RESET_ZONE);

        // 두 트랜잭션이 동시에 첫 진입해도 정확히 한 row만 만들어진다. 이미 있으면 no-op.
        userChatUsageRepository.insertIfAbsent(userId, today);

        // 위 단계로 row 존재가 보장되므로 findForUpdate는 항상 결과가 있어야 한다.
        UserChatUsage usage = userChatUsageRepository.findForUpdate(userId, today)
                .orElseThrow(() -> new IllegalStateException(
                        "user_chat_usage row missing after upsert: userId=" + userId + ", date=" + today));

        if (usage.getChatCount() >= limit) {
            throw new RateLimitExceededException(0, nextResetInstant(today));
        }
        usage.increment();
        return limit - usage.getChatCount();
    }

    /**
     * 메시지 전송 직전 차감 후 후속 검증(세션 소유 확인 등)이 실패한 경우 호출.
     * 별도 트랜잭션으로 즉시 -1 반영해 사용자가 한도 1회를 부당하게 잃지 않도록 한다.
     * row가 없거나 chat_count가 이미 0이면 안전하게 no-op.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrement(Long userId) {
        LocalDate today = LocalDate.now(RESET_ZONE);
        userChatUsageRepository.findForUpdate(userId, today)
                .ifPresent(UserChatUsage::decrement);
    }

    @Transactional(readOnly = true)
    public ChatUsageResponse getUsage(Long userId) {
        User user = findUser(userId);
        int limit = user.getDailyChatLimit();
        LocalDate today = LocalDate.now(RESET_ZONE);
        int used = userChatUsageRepository.findByUserIdAndUsageDate(userId, today)
                .map(UserChatUsage::getChatCount)
                .orElse(0);
        return new ChatUsageResponse(
                limit,
                used,
                Math.max(limit - used, 0),
                nextResetInstant(today)
        );
    }

    @Transactional
    public ChatUsageResponse resetTodayUsage(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        LocalDate today = LocalDate.now(RESET_ZONE);
        userChatUsageRepository.findForUpdate(userId, today)
                .ifPresent(UserChatUsage::resetToZero);
        return getUsage(userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private java.time.Instant nextResetInstant(LocalDate today) {
        // 다음 날 00:00 KST → Instant (UTC ISO-8601 직렬화)
        return today.plusDays(1).atStartOfDay(RESET_ZONE).toInstant();
    }
}
