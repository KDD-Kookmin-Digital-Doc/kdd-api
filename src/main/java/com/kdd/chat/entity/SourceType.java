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
    FAQ("faq"),
    // 추천 질문(GET /chat/recommended-questions) 클릭으로 자동 생성된 세션 — FAQ 인입 스케줄러의
    // ChatMessageRepository.findRecentUserQuestionContents 입력에서 제외되어, 추천 클릭 → 클러스터링 →
    // 또 다른 추천의 피드백 루프를 차단한다.
    RECOMMENDED("recommended");

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
