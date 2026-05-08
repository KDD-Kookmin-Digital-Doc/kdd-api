package com.kdd.faq.dto;

import com.kdd.faq.entity.FaqTopic;

public record FaqTopicResponse(
        FaqTopic topic,
        String label
) {
    public static FaqTopicResponse from(FaqTopic topic) {
        return new FaqTopicResponse(topic, topic.getLabel());
    }
}
