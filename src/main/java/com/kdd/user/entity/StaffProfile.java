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
@Table(name = "staff_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 30)
    private StaffDepartment department;

    @Column(name = "job_description", columnDefinition = "text")
    private String jobDescription;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public StaffProfile(User user, StaffDepartment department, String jobDescription) {
        this.user = user;
        this.department = department;
        this.jobDescription = (jobDescription == null || jobDescription.isBlank()) ? null : jobDescription;
    }

    public void update(StaffDepartment department, String jobDescription) {
        if (department != null) this.department = department;
        if (jobDescription != null) this.jobDescription = jobDescription.isBlank() ? null : jobDescription;
    }
}
