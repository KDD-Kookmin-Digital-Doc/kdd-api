package com.kdd.chat.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageRole {

    USER("user"),
    ASSISTANT("assistant");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    public static MessageRole from(String value) {
        for (MessageRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown MessageRole: " + value);
    }

    @Converter(autoApply = true)
    public static class MessageRoleConverter implements AttributeConverter<MessageRole, String> {
        @Override
        public String convertToDatabaseColumn(MessageRole role) {
            return role == null ? null : role.getValue();
        }

        @Override
        public MessageRole convertToEntityAttribute(String value) {
            return value == null ? null : MessageRole.from(value);
        }
    }
}
