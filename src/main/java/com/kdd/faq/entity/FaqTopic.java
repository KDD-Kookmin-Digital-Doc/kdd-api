package com.kdd.faq.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FaqTopic {

    ACADEMIC("academic", "학사"),
    GRADUATION("graduation", "졸업"),
    ENROLLMENT_STATUS("enrollment_status", "휴학·복학·자퇴"),
    SCHOLARSHIP("scholarship", "장학"),
    REGISTRATION("registration", "등록·학적"),
    CURRICULUM("curriculum", "전공·교과"),
    CAREER("career", "취업·현장실습"),
    EVENT("event", "행사·특강"),
    OTHER("other", "기타");

    private final String value;
    private final String label;

    @JsonValue
    public String getValue() {
        return value;
    }

    public static FaqTopic from(String value) {
        for (FaqTopic topic : values()) {
            if (topic.value.equals(value)) {
                return topic;
            }
        }
        throw new IllegalArgumentException("Unknown FaqTopic: " + value);
    }

    @Converter(autoApply = true)
    public static class FaqTopicConverter implements AttributeConverter<FaqTopic, String> {
        @Override
        public String convertToDatabaseColumn(FaqTopic topic) {
            return topic == null ? null : topic.getValue();
        }

        @Override
        public FaqTopic convertToEntityAttribute(String value) {
            return value == null ? null : FaqTopic.from(value);
        }
    }
}
