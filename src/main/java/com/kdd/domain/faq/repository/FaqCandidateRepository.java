package com.kdd.domain.faq.repository;

import com.kdd.domain.faq.entity.FaqCandidate;
import com.kdd.domain.faq.entity.FaqCandidateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqCandidateRepository extends JpaRepository<FaqCandidate, Long> {

    List<FaqCandidate> findByStatusOrderByCreatedAtDesc(FaqCandidateStatus status);
}
