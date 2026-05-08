package com.kdd.faq.dto;

import com.kdd.faq.entity.Faq;
import com.kdd.faq.entity.FaqTopic;

import java.time.LocalDateTime;

public record FaqResponse(
        Long faqId,
        String question,
        String answer,
        FaqTopic topic,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FaqResponse from(Faq faq) {
        return new FaqResponse(
                faq.getId(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getTopic(),
                faq.getCreatedAt(),
                faq.getUpdatedAt()
        );
    }
}
