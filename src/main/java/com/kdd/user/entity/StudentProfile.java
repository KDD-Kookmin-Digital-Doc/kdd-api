package com.kdd.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_profiles", indexes = {
        @Index(name = "idx_student_profile_student_id", columnList = "student_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "student_id", nullable = false, length = 30)
    private String studentId;

    @Column(nullable = false, length = 20)
    private StudentDepartment department;

    @Column(nullable = false)
    private Short grade;

    @Column(name = "admission_year", nullable = false)
    private Short admissionYear;

    @Column(name = "academic_status", nullable = false, length = 20)
    private AcademicStatus academicStatus = AcademicStatus.ENROLLED;

    @Column(name = "additional_info", columnDefinition = "text")
    private String additionalInfo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public StudentProfile(User user, String studentId, StudentDepartment department,
                           Short grade, Short admissionYear, AcademicStatus academicStatus,
                           String additionalInfo) {
        this.user = user;
        this.studentId = studentId;
        this.department = department;
        this.grade = grade;
        this.admissionYear = admissionYear;
        this.academicStatus = (academicStatus == null) ? AcademicStatus.ENROLLED : academicStatus;
        this.additionalInfo = (additionalInfo == null || additionalInfo.isBlank()) ? null : additionalInfo;
    }

    public void update(String studentId, StudentDepartment department, Short grade,
                       Short admissionYear, AcademicStatus academicStatus, String additionalInfo) {
        if (studentId != null && !studentId.isBlank()) this.studentId = studentId;
        if (department != null) this.department = department;
        if (grade != null) this.grade = grade;
        if (admissionYear != null) this.admissionYear = admissionYear;
        if (academicStatus != null) this.academicStatus = academicStatus;
        if (additionalInfo != null) this.additionalInfo = additionalInfo.isBlank() ? null : additionalInfo;
    }
}
