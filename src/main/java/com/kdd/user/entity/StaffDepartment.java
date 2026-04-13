package com.kdd.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StaffDepartment {

    STUDENT_SUPPORT("student_support", "학생지원팀"),
    ACADEMIC_AFFAIRS("academic_affairs", "학사팀"),
    ADMISSIONS("admissions", "입학팀"),
    INDUSTRY_COOPERATION("industry_cooperation", "산학협력팀"),
    INTERNATIONAL_OFFICE("international_office", "국제교류팀"),
    GENERAL_AFFAIRS("general_affairs", "총무팀"),
    OTHER("other", "기타");

    private final String value;
    private final String displayName;

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
