package com.kdd.chat.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConfidenceLevel {

    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ConfidenceLevel from(String value) {
        for (ConfidenceLevel level : values()) {
            if (level.value.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown ConfidenceLevel: " + value);
    }

    @Converter(autoApply = true)
    public static class ConfidenceLevelConverter implements AttributeConverter<ConfidenceLevel, String> {
        @Override
        public String convertToDatabaseColumn(ConfidenceLevel level) {
            return level == null ? null : level.getValue();
        }

        @Override
        public ConfidenceLevel convertToEntityAttribute(String value) {
            return value == null ? null : ConfidenceLevel.from(value);
        }
    }
}
