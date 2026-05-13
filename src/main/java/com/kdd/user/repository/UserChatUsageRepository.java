package com.kdd.user.repository;

import com.kdd.user.entity.UserChatUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserChatUsageRepository extends JpaRepository<UserChatUsage, Long> {

    Optional<UserChatUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);

    // 동일 사용자가 동시에 채팅을 보낼 때 한 row를 두고 chat_count++가 경합하는 것을 막기 위해
    // SELECT FOR UPDATE로 행 잠금을 잡은 뒤 증가시킨다. 한 사용자의 짧은 트랜잭션이라 락 경합은 무시 가능.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserChatUsage u WHERE u.userId = :userId AND u.usageDate = :usageDate")
    Optional<UserChatUsage> findForUpdate(@Param("userId") Long userId,
                                          @Param("usageDate") LocalDate usageDate);

    @Query("SELECT u FROM UserChatUsage u WHERE u.userId IN :userIds AND u.usageDate = :usageDate")
    List<UserChatUsage> findAllByUserIdsAndDate(@Param("userIds") List<Long> userIds,
                                                 @Param("usageDate") LocalDate usageDate);

    // 동시 첫 진입 race condition 방어용 upsert. JPA의 saveAndFlush + catch 패턴은
    // PostgreSQL이 unique violation 시 트랜잭션 전체를 abort 상태로 만들어 후속 쿼리가 실패하므로
    // INSERT ... ON CONFLICT DO NOTHING로 한 번에 처리한다. 이후 findForUpdate가 같은 row를 잠근다.
    @Modifying
    @Query(value = """
            INSERT INTO user_chat_usage (user_id, usage_date, chat_count, created_at, updated_at)
            VALUES (:userId, :usageDate, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, usage_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("usageDate") LocalDate usageDate);
}
