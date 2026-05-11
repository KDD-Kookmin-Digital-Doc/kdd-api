package com.kdd.auth.service;

import com.kdd.auth.entity.AuthSession;
import com.kdd.auth.repository.AuthSessionRepository;
import com.kdd.global.security.SessionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenReuseDetector {

    private final AuthSessionRepository authSessionRepository;
    private final SessionValidator sessionValidator;

    /**
     * 호출 측 트랜잭션이 곧 INVALID_REFRESH_TOKEN 예외로 롤백되더라도,
     * 도난 의심 세션 revoke만은 독립 트랜잭션으로 commit되어야 한다.
     * 자연 만료(revokedAt == null)는 도난이 아니므로 무시.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void detectAndRevoke(String refreshTokenHash, LocalDateTime now) {
        authSessionRepository.findByRefreshTokenHash(refreshTokenHash).ifPresent(reused -> {
            if (reused.getRevokedAt() == null) {
                return;
            }
            Long userId = reused.getUser().getId();
            List<AuthSession> activeSessions = authSessionRepository.findAllActiveByUserId(userId);
            for (AuthSession s : activeSessions) {
                s.revoke();
                sessionValidator.invalidate(s.getId());
            }
            log.warn("Refresh token reuse detected — {} session(s) revoked: userId={}, triggerSessionId={}",
                    activeSessions.size(), userId, reused.getId());
        });
    }
}
