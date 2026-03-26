package com.kdd.domain.user.entity;

import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", indexes = {
        @Index(columnList = "email"),
        @Index(columnList = "user_type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType userType;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private boolean isProfileCompleted;

    @Builder
    public User(String email, String name, UserRole role, UserType userType) {
        this.email = email;
        this.name = name;
        this.role = role != null ? role : UserRole.USER;
        this.userType = userType;
        this.isActive = true;
        this.isProfileCompleted = false;
    }
}
