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
     * 자연 만료 시각이 보존 기간을 지난 세션을 한 배치만큼 삭제한다 (#69).
     *
     * <p><b>revoke 여부를 술어에 두지 않는 이유</b>: revoked_at만 보고 즉시 지우면
     * {@link com.kdd.auth.service.RefreshTokenReuseDetector}가 사용하는 hash 매핑이 사라져
     * 도난 토큰 재사용 시 전체 세션 revoke를 못 한다. 원래 refresh token의 자연 만료
     * (expires_at) 이전까지는 reuse detection 가치가 살아있으므로 revoked 행도 그 시각이
     * 지날 때까지 보존하고, 보존 기간(expiredCutoff) 이후에 일괄 삭제한다.
     *
     * <p><b>배치 처리</b>: 한 번에 모두 지우면 누적된 첫 운영 실행에서 lock/WAL 부담이
     * 커지므로 호출 측이 작은 배치로 반복 호출해 각 배치를 짧은 별도 트랜잭션으로 commit한다.
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
                WHERE expires_at < :expiredCutoff
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredBatch(
            @Param("expiredCutoff") LocalDateTime expiredCutoff,
            @Param("batchSize") int batchSize);
}
