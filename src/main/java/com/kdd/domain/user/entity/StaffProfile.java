package com.kdd.domain.user.entity;

import com.kdd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "staff_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffProfile extends BaseTimeEntity {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffDepartment department;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @Builder
    public StaffProfile(User user, StaffDepartment department, String jobDescription) {
        this.user = user;
        this.department = department;
        this.jobDescription = jobDescription;
    }
}
