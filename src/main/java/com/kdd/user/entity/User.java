package com.kdd.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_user_type", columnList = "user_type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType = UserType.STUDENT;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "is_profile_completed", nullable = false)
    private boolean isProfileCompleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public User(String email, String name, Role role) {
        this.email = email;
        this.name = name;
        this.role = (role == null) ? Role.USER : role;
        this.userType = UserType.STUDENT;
        this.isActive = true;
        this.isProfileCompleted = false;
    }

    public void completeProfile(UserType userType) {
        this.userType = Objects.requireNonNull(userType, "userType must not be null");
        this.isProfileCompleted = true;
    }

    public void resetProfile() {
        this.isProfileCompleted = false;
    }

    public void updateName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }
}
