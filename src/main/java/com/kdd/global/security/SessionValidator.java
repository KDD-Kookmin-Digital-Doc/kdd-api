package com.kdd.global.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kdd.auth.entity.AuthSession;
import com.kdd.auth.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    public void invalidate(Long sessionId) {
        validityCache.invalidate(sessionId);
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
