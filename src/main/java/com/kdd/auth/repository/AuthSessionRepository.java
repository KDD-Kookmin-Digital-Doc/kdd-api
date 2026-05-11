package com.kdd.auth.repository;

import com.kdd.auth.entity.AuthSession;
import com.kdd.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AuthSession s JOIN FETCH s.user WHERE s.refreshTokenHash = :hash AND s.revokedAt IS NULL AND s.expiresAt > :now")
    Optional<AuthSession> findValidSessionForUpdate(
            @Param("hash") String hash,
            @Param("now") LocalDateTime now);

    @Query("SELECT s FROM AuthSession s JOIN FETCH s.user WHERE s.refreshTokenHash = :hash")
    Optional<AuthSession> findByRefreshTokenHash(@Param("hash") String hash);

    @Query("SELECT s FROM AuthSession s JOIN FETCH s.user WHERE s.id = :id")
    Optional<AuthSession> findByIdWithUser(@Param("id") Long id);

    List<AuthSession> findAllByUserAndRevokedAtIsNull(User user);

    @Modifying
    @Query("UPDATE AuthSession s SET s.revokedAt = :now WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    void revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
