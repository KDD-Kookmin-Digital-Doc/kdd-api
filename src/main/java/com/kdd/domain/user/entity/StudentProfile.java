package com.kdd.domain.user.entity;

import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "student_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfile extends BaseTimeEntity {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, unique = true, length = 30)
    private String studentId;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(nullable = false)
    private Integer grade;

    @Column(nullable = false)
    private Integer admissionYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcademicStatus academicStatus;

    @Column(columnDefinition = "TEXT")
    private String additionalInfo;

    @Builder
    public StudentProfile(User user, String studentId, String department,
                          Integer grade, Integer admissionYear,
                          AcademicStatus academicStatus, String additionalInfo) {
        this.user = user;
        this.studentId = studentId;
        this.department = department;
        this.grade = grade;
        this.admissionYear = admissionYear;
        this.academicStatus = academicStatus != null ? academicStatus : AcademicStatus.ENROLLED;
        this.additionalInfo = additionalInfo;
    }
}
