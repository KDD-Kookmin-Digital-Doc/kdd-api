package com.kdd.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_chat_usage",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_chat_usage_user_date",
                columnNames = {"user_id", "usage_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserChatUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "chat_count", nullable = false)
    private int chatCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public UserChatUsage(Long userId, LocalDate usageDate, int chatCount) {
        this.userId = userId;
        this.usageDate = usageDate;
        this.chatCount = chatCount;
    }

    public void increment() {
        this.chatCount += 1;
    }

    /**
     * 메시지 전송 직전 차감 후 후속 검증(세션 소유 확인 등)이 실패한 경우 롤백용.
     * 음수가 되지 않도록 0 미만이면 변경하지 않는다.
     */
    public void decrement() {
        if (this.chatCount > 0) {
            this.chatCount -= 1;
        }
    }

    public void resetToZero() {
        this.chatCount = 0;
    }
}
