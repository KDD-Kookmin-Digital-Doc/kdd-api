package com.kdd.user.repository;

import com.kdd.user.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    Optional<StaffProfile> findByUserId(Long userId);
}
