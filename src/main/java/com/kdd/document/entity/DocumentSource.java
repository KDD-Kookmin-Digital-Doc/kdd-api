package com.kdd.document.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentSource {

    SW("SW"),
    KMU("KMU");

    private final String value;

    // 요청·응답·DB 모두 대문자("SW"/"KMU")로 통일. 노션 API 명세(2026-05-17판)가 대문자 리터럴이며
    // FE 타입도 이미 "SW" | "KMU"로 정의되어 있다. #60에서 lowercase로 일시 통일했던 것을 명세 정합으로 되돌림.
    @JsonValue
    public String toJson() {
        return value;
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
