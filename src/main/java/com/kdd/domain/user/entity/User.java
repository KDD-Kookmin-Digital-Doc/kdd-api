package com.kdd.domain.user.entity;

import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    private UserType userType;

    private String department;

    private String studentId;

    private Integer grade;

    private Integer admissionYear;

    @Enumerated(EnumType.STRING)
    private AcademicStatus academicStatus;

    @Builder
    public User(String email, String name, UserRole role, UserType userType,
                String department, String studentId, Integer grade,
                Integer admissionYear, AcademicStatus academicStatus) {
        this.email = email;
        this.name = name;
        this.role = role != null ? role : UserRole.USER;
        this.userType = userType;
        this.department = department;
        this.studentId = studentId;
        this.grade = grade;
        this.admissionYear = admissionYear;
        this.academicStatus = academicStatus;
    }
}
