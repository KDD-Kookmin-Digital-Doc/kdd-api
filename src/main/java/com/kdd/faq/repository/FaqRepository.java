package com.kdd.faq.repository;

import com.kdd.faq.entity.Faq;
import com.kdd.faq.entity.FaqTopic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    Page<Faq> findByTopic(FaqTopic topic, Pageable pageable);
}
