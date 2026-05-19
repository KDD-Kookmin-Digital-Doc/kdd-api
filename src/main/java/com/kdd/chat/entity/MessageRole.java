package com.kdd.chat.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 메시지 발신자 역할. DB 컬럼은 lowercase 문자열로 저장된다.
 * <p>
 * <b>주의</b>: JPQL은 {@code MessageRole.USER} enum 참조로 안전하지만, native query는
 * {@code 'user'}/{@code 'assistant'} 리터럴을 직접 사용한다 (예: DocumentRepository의
 * popular 쿼리, StatisticsRepository의 집계 쿼리). enum value 문자열을 바꾸면 native 쪽은
 * 자동 동기화되지 않으니 native 쿼리 리터럴도 함께 수정해야 한다.
 */
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
