package com.kdd.auth.service;

import com.kdd.auth.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenReuseDetector {

    private final AuthSessionRepository authSessionRepository;

    /**
     * 호출 측 트랜잭션이 곧 INVALID_REFRESH_TOKEN 예외로 롤백되더라도,
     * 도난 의심 세션 revoke만은 독립 트랜잭션으로 commit되어야 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void detectAndRevoke(String refreshTokenHash, LocalDateTime now) {
        authSessionRepository.findByRefreshTokenHash(refreshTokenHash).ifPresent(reused -> {
            Long userId = reused.getUser().getId();
            authSessionRepository.revokeAllByUserId(userId, now);
            log.warn("Refresh token reuse detected — all sessions revoked: userId={}, sessionId={}",
                    userId, reused.getId());
        });
    }
}
