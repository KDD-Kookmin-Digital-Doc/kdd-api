package com.kdd.document.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentStatus {

    UPLOADED("uploaded"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    REPROCESSING("reprocessing");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    public static DocumentStatus from(String value) {
        for (DocumentStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown DocumentStatus: " + value);
    }

    @Converter(autoApply = true)
    public static class DocumentStatusConverter implements AttributeConverter<DocumentStatus, String> {
        @Override
        public String convertToDatabaseColumn(DocumentStatus status) {
            return status == null ? null : status.getValue();
        }

        @Override
        public DocumentStatus convertToEntityAttribute(String value) {
            return value == null ? null : DocumentStatus.from(value);
        }
    }
}
