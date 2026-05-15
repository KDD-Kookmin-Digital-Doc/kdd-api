package com.kdd.faq.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
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

    /**
     * 관리자 후보 목록의 optional status 필터용. 빈/공백은 null(전체 상태)로,
     * 그 외 알 수 없는 값은 INVALID_INPUT(400)으로 컨버트해 컨트롤러 보일러플레이트를 줄인다.
     */
    public static FaqCandidateStatus parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return from(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
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
