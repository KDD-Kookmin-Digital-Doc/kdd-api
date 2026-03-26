package com.kdd.domain.auth.repository;

import com.kdd.domain.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
}
