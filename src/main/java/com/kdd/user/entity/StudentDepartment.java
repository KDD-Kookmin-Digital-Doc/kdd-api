package com.kdd.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StudentDepartment {

    SOFTWARE("software", "소프트웨어학부"),
    AI("ai", "인공지능학부");

    private final String value;
    private final String displayName;

    public static StudentDepartment from(String value) {
        for (StudentDepartment dept : values()) {
            if (dept.value.equals(value)) {
                return dept;
            }
        }
        throw new IllegalArgumentException("Unknown student department: " + value);
    }

    @Converter(autoApply = true)
    public static class StudentDepartmentConverter implements AttributeConverter<StudentDepartment, String> {

        @Override
        public String convertToDatabaseColumn(StudentDepartment dept) {
            return dept == null ? null : dept.getValue();
        }

        @Override
        public StudentDepartment convertToEntityAttribute(String value) {
            return value == null ? null : StudentDepartment.from(value);
        }
    }
}
