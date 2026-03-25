package com.kdd.auth.repository;

import com.kdd.auth.entity.AuthSession;
import com.kdd.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByRefreshTokenHashAndRevokedAtIsNull(String refreshTokenHash);

    List<AuthSession> findAllByUserAndRevokedAtIsNull(User user);
}
