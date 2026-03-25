package com.kdd.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserType {

    STUDENT("student"),
    STAFF("staff");

    private final String value;

    public static UserType from(String value) {
        for (UserType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown user type: " + value);
    }

    @Converter(autoApply = true)
    public static class UserTypeConverter implements AttributeConverter<UserType, String> {

        @Override
        public String convertToDatabaseColumn(UserType userType) {
            return userType == null ? null : userType.getValue();
        }

        @Override
        public UserType convertToEntityAttribute(String value) {
            return value == null ? null : UserType.from(value);
        }
    }
}
