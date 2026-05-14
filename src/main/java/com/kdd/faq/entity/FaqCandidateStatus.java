package com.kdd.faq.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * FAQ 후보의 검토 상태.
 * <ul>
 *     <li>{@code PENDING} — 스케줄러가 생성한 직후, 관리자 검토 대기.</li>
 *     <li>{@code APPROVED} — 관리자가 카테고리를 지정해 실제 FAQ로 등록한 상태. 후보 row는 audit 용도로 보존.</li>
 *     <li>{@code REJECTED} — 관리자가 반려한 상태. 후보 row는 보존되며 목록에서 필터로 가린다.</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum FaqCandidateStatus {

    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    public static FaqCandidateStatus from(String value) {
        for (FaqCandidateStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown FaqCandidateStatus: " + value);
    }

    @Converter(autoApply = true)
    public static class FaqCandidateStatusConverter implements AttributeConverter<FaqCandidateStatus, String> {
        @Override
        public String convertToDatabaseColumn(FaqCandidateStatus status) {
            return status == null ? null : status.getValue();
        }

        @Override
        public FaqCandidateStatus convertToEntityAttribute(String value) {
            return value == null ? null : FaqCandidateStatus.from(value);
        }
    }
}
