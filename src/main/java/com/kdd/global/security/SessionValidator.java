package com.kdd.global.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kdd.auth.entity.AuthSession;
import com.kdd.auth.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SessionValidator {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final long CACHE_MAX_SIZE = 10_000L;

    private final AuthSessionRepository authSessionRepository;

    private final Cache<Long, Boolean> validityCache = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .maximumSize(CACHE_MAX_SIZE)
            .build();

    public boolean isValid(Long sessionId) {
        return validityCache.get(sessionId, this::lookupValidity);
    }

    /**
     * 호출 시점이 트랜잭션 안이면 commit 직후에 캐시를 비운다.
     * commit 전에 비우면 동시 reader가 commit 안 된 (살아있어 보이는) 상태를
     * 다시 캐시에 박을 수 있어 30s lag이 부활하기 때문.
     * 트랜잭션 밖에서 호출되면 즉시 비운다.
     */
    public void invalidate(Long sessionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    validityCache.invalidate(sessionId);
                }
            });
        } else {
            validityCache.invalidate(sessionId);
        }
    }

    private boolean lookupValidity(Long sessionId) {
        return authSessionRepository.findByIdWithUser(sessionId)
                .map(this::isLive)
                .orElse(false);
    }

    private boolean isLive(AuthSession session) {
        return session.getRevokedAt() == null
                && session.getExpiresAt().isAfter(LocalDateTime.now())
                && session.getUser().isActive();
    }
}
