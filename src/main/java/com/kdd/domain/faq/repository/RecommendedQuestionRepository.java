package com.kdd.domain.faq.repository;

import com.kdd.domain.faq.entity.RecommendedQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendedQuestionRepository extends JpaRepository<RecommendedQuestion, Long> {
}
