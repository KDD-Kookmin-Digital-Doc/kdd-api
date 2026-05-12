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

    // 입력(API 요청 body)은 lowercase value("sw", "kmu")로 받고
    // DB 컬럼도 lowercase로 저장하므로, 응답 직렬화도 lowercase로 통일한다 (#60).
    // 기존엔 name()이라 응답은 "SW"인데 같은 값을 그대로 요청에 넣으면 400으로 거절됐다.
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
