package com.kdd.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AcademicStatus {

    ENROLLED("enrolled", "재학생"),
    ON_LEAVE("on_leave", "휴학생"),
    RETURNING("returning", "복학생");

    private final String value;
    private final String displayName;

    public static AcademicStatus from(String value) {
        for (AcademicStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown academic status: " + value);
    }

    @Converter(autoApply = true)
    public static class AcademicStatusConverter implements AttributeConverter<AcademicStatus, String> {

        @Override
        public String convertToDatabaseColumn(AcademicStatus status) {
            return status == null ? null : status.getValue();
        }

        @Override
        public AcademicStatus convertToEntityAttribute(String value) {
            return value == null ? null : AcademicStatus.from(value);
        }
    }
}
