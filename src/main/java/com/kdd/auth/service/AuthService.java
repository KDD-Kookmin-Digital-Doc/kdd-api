package com.kdd.auth.service;

import com.kdd.auth.dto.GoogleUserInfo;
import com.kdd.auth.entity.AuthSession;
import com.kdd.auth.repository.AuthSessionRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.global.security.JwtProvider;
import com.kdd.user.entity.Role;
import com.kdd.user.entity.User;
import com.kdd.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleOAuthService googleOAuthService;
    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final JwtProvider jwtProvider;

    @Value("${app.auth.allowed-domain}")
    private String allowedDomain;

    @Value("${app.auth.admin-emails:}")
    private String adminEmails;

    @Value("${app.jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Transactional
    public LoginResult login(String code) {
        GoogleUserInfo userInfo = googleOAuthService.verifyAndExtract(code);

        validateDomain(userInfo.email());

        User user = userRepository.findByEmail(userInfo.email())
                .orElseGet(() -> createUser(userInfo));

        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DEACTIVATED);
        }

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtProvider.generateRefreshToken();

        revokeExistingSessions(user);
        saveAuthSession(user, refreshToken);

        return new LoginResult(accessToken, refreshToken, user.isProfileCompleted());
    }

    @Transactional
    public RefreshResult refresh(String refreshToken) {
        String hash = hashToken(refreshToken);

        AuthSession session = authSessionRepository
                .findValidSessionForUpdate(hash, LocalDateTime.now())
                .orElseThrow(() -> {
                    log.warn("Invalid refresh token attempt: hash={}", hash);
                    return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
                });

        User user = session.getUser();

        if (!user.isActive()) {
            log.warn("Refresh attempt by deactivated account: userId={}", user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_DEACTIVATED);
        }

        String newAccessToken = jwtProvider.generateAccessToken(user.getId(), user.getRole().name());
        String newRefreshToken = jwtProvider.generateRefreshToken();

        session.updateLastUsedAt();
        session.revoke();
        saveAuthSession(user, newRefreshToken);

        return new RefreshResult(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(Long userId) {
        authSessionRepository.findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(AuthSession::revoke);
        log.info("User logged out: userId={}", userId);
    }

    private void validateDomain(String email) {
        if (!email.endsWith("@" + allowedDomain)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_DOMAIN);
        }
    }

    private User createUser(GoogleUserInfo userInfo) {
        Role role = isAdminEmail(userInfo.email()) ? Role.ADMIN : Role.USER;

        User user = User.builder()
                .email(userInfo.email())
                .name(userInfo.name())
                .role(role)
                .build();

        return userRepository.save(user);
    }

    private boolean isAdminEmail(String email) {
        if (adminEmails == null || adminEmails.isBlank()) {
            return false;
        }
        List<String> adminEmailList = Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .toList();
        return adminEmailList.contains(email);
    }

    private void revokeExistingSessions(User user) {
        authSessionRepository.findAllByUserAndRevokedAtIsNull(user)
                .forEach(AuthSession::revoke);
    }

    private void saveAuthSession(User user, String refreshToken) {
        String hash = hashToken(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(refreshTokenExpiry / 1000);

        AuthSession session = AuthSession.builder()
                .user(user)
                .refreshTokenHash(hash)
                .expiresAt(expiresAt)
                .build();

        authSessionRepository.save(session);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public record LoginResult(String accessToken, String refreshToken, boolean isProfileCompleted) {
    }

    public record RefreshResult(String accessToken, String refreshToken) {
    }
}