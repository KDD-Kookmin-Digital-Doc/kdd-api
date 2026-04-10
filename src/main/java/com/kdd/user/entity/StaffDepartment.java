package com.kdd.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StaffDepartment {

    STUDENT_SUPPORT("student_support"),
    ACADEMIC_AFFAIRS("academic_affairs"),
    ADMISSIONS("admissions"),
    INDUSTRY_COOPERATION("industry_cooperation"),
    INTERNATIONAL_OFFICE("international_office"),
    GENERAL_AFFAIRS("general_affairs"),
    OTHER("other");

    private final String value;

    public static StaffDepartment from(String value) {
        for (StaffDepartment dept : values()) {
            if (dept.value.equals(value)) {
                return dept;
            }
        }
        throw new IllegalArgumentException("Unknown staff department: " + value);
    }

    @Converter(autoApply = true)
    public static class StaffDepartmentConverter implements AttributeConverter<StaffDepartment, String> {

        @Override
        public String convertToDatabaseColumn(StaffDepartment dept) {
            return dept == null ? null : dept.getValue();
        }

        @Override
        public StaffDepartment convertToEntityAttribute(String value) {
            return value == null ? null : StaffDepartment.from(value);
        }
    }
}
