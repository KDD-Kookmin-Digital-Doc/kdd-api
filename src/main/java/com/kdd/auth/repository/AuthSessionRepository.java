package com.kdd.auth.repository;

import com.kdd.auth.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    @Query("SELECT s FROM AuthSession s WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    List<AuthSession> findAllActiveByUserId(@Param("userId") Long userId);

    /**
     * revoke된 세션, 또는 만료된 지 보존 기간이 지난 세션을 한 배치만큼 삭제한다 (#69).
     * 한 번에 모두 지우면 누적된 첫 운영 실행에서 lock/WAL 부담이 커질 수 있으므로,
     * 호출 측이 작은 배치로 반복 호출해 각 배치를 짧은 별도 트랜잭션으로 commit한다.
     * 매 호출은 자체 트랜잭션으로 동작해야 하므로 메서드에 @Transactional을 둔다.
     *
     * @param expiredCutoff 이 시각 이전에 만료된 세션을 삭제 대상으로 본다
     * @param batchSize     이번 호출에서 삭제할 최대 행 수
     * @return 실제 삭제된 행 수 (batchSize 미만이면 더 이상 지울 행이 없다는 신호)
     */
    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM auth_sessions
            WHERE id IN (
                SELECT id FROM auth_sessions
                WHERE revoked_at IS NOT NULL OR expires_at < :expiredCutoff
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteRevokedOrExpiredBatch(
            @Param("expiredCutoff") LocalDateTime expiredCutoff,
            @Param("batchSize") int batchSize);
}
