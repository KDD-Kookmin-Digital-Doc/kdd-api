package com.kdd.chat.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SourceType {

    NORMAL("normal"),
    FAQ("faq");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    public static SourceType from(String value) {
        for (SourceType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SourceType: " + value);
    }

    @Converter(autoApply = true)
    public static class SourceTypeConverter implements AttributeConverter<SourceType, String> {
        @Override
        public String convertToDatabaseColumn(SourceType sourceType) {
            return sourceType == null ? null : sourceType.getValue();
        }

        @Override
        public SourceType convertToEntityAttribute(String value) {
            return value == null ? null : SourceType.from(value);
        }
    }
}
