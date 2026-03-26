package com.kdd.domain.faq.repository;

import com.kdd.domain.faq.entity.Faq;
import com.kdd.domain.faq.entity.FaqCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findByCategoryOrderByCreatedAtDesc(FaqCategory category);
}
