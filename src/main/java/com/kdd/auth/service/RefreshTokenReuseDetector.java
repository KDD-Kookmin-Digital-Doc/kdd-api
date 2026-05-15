package com.kdd.auth.service;

import com.kdd.auth.entity.AuthSession;
import com.kdd.auth.repository.AuthSessionRepository;
import com.kdd.global.security.SessionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenReuseDetector {

    private final AuthSessionRepository authSessionRepository;
    private final SessionValidator sessionValidator;

    @Value("${app.auth.refresh-reuse-grace-period}")
    private long gracePeriodMillis;

    /**
     * 호출 측 트랜잭션이 곧 INVALID_REFRESH_TOKEN 예외로 롤백되더라도,
     * 도난 의심 세션 revoke만은 독립 트랜잭션으로 commit되어야 한다.
     * 자연 만료(revokedAt == null)는 도난이 아니므로 무시.
     *
     * @param now AuthService.refresh() 진입 시점에 캡처된 시각. detector 도달까지의
     *            지연(수십 ms) 만큼 grace window가 보수적으로 줄어들지만, grace가 초 단위라
     *            실효 영향은 무시 가능. LocalDateTime.now()로 재캡처하지 않는 이유는
     *            호출 측이 이미 같은 now를 query/예외 경로에 쓰고 있어 일관성을 유지하기 위해서다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void detectAndRevoke(String refreshTokenHash, LocalDateTime now) {
        authSessionRepository.findByRefreshTokenHash(refreshTokenHash).ifPresent(reused -> {
            if (reused.getRevokedAt() == null) {
                return;
            }
            // 정상적인 동시 refresh race: FE 인터셉터가 동시 401에 refresh를 두 번 발동하면
            // 두 번째 호출이 방금 revoke된 토큰을 들고 들어온다. revoke 직후 짧은 유예 시간
            // 이내의 재사용은 도난이 아닌 race로 보고 전체 세션 revoke를 건너뛴다 (#76).
            if (reused.getRevokedAt().isAfter(now.minus(gracePeriodMillis, ChronoUnit.MILLIS))) {
                // grace window 발동 빈도는 grace period 튜닝 근거가 되므로 info로 기록한다.
                // 노이즈가 과도하면 grace period 값을 좁히거나 레벨을 debug로 내릴 것.
                log.info("Refresh token reuse within grace period — treated as concurrent refresh race: triggerSessionId={}",
                        reused.getId());
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
