package com.kdd.domain.user.repository;

import com.kdd.domain.user.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {
}
