package com.kdd.document.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentSource {

    SW("sw"),
    KMU("kmu");

    private final String value;

    @JsonValue
    public String toJson() {
        return name();
    }

    public static DocumentSource from(String value) {
        for (DocumentSource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown DocumentSource: " + value);
    }

    @Converter(autoApply = true)
    public static class DocumentSourceConverter implements AttributeConverter<DocumentSource, String> {
        @Override
        public String convertToDatabaseColumn(DocumentSource source) {
            return source == null ? null : source.getValue();
        }

        @Override
        public DocumentSource convertToEntityAttribute(String value) {
            return value == null ? null : DocumentSource.from(value);
        }
    }
}
