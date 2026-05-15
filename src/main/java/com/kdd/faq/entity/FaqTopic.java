package com.kdd.faq.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

@Getter
@RequiredArgsConstructor
public enum FaqTopic {

    ACADEMIC("academic", "학사"),
    GRADUATION("graduation", "졸업"),
    ENROLLMENT_STATUS("enrollment_status", "휴학·복학·자퇴"),
    SCHOLARSHIP("scholarship", "장학"),
    REGISTRATION("registration", "등록·학적"),
    CURRICULUM("curriculum", "전공·교과"),
    CAREER("career", "취업·현장실습"),
    EVENT("event", "행사·특강"),
    OTHER("other", "기타");

    // enum은 @Slf4j 부착 불가하므로 static logger 직접 선언.
    private static final Logger log = LoggerFactory.getLogger(FaqTopic.class);

    private final String value;
    private final String label;

    @JsonValue
    public String getValue() {
        return value;
    }

    public static FaqTopic from(String value) {
        for (FaqTopic topic : values()) {
            if (topic.value.equals(value)) {
                return topic;
            }
        }
        throw new IllegalArgumentException("Unknown FaqTopic: " + value);
    }

    /**
     * 외부 입력(FE 쿼리 파라미터, Request body)을 받아 FaqTopic으로 변환.
     * 잘못된 값은 INVALID_INPUT(400)으로 변환되어 컨트롤러/서비스가 try/catch 보일러플레이트를 반복하지 않도록 한다.
     * 잘못된 입력값 자체는 운영 디버깅을 위해 log.warn에 남긴다 (Sentry/log aggregator에서 추적 가능).
     */
    public static FaqTopic parseOrThrow(String value) {
        try {
            return from(value);
        } catch (IllegalArgumentException e) {
            // 로그 인젝션·과대 페이로드 방지: 개행 제거 + 길이 제한 후 기록
            log.warn("Invalid FaqTopic input: {}", sanitizeForLog(value));
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // ASCII 제어문자(CR/LF/TAB/NUL/ANSI ESC 등) + Unicode line separator(U+2028, U+2029) 모두 치환.
    // log aggregator·터미널이 이 문자들을 만나면 라인 분리/시각 위조 가능하므로 안전한 underscore로.
    private static final Pattern UNSAFE_LOG_CHARS = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]");

    private static String sanitizeForLog(String value) {
        if (value == null) return "null";
        String safe = UNSAFE_LOG_CHARS.matcher(value).replaceAll("_");
        // 접미사 길이까지 포함해 정확히 64자로 자른다 (61 + "..." = 64).
        return safe.length() > 64 ? safe.substring(0, 61) + "..." : safe;
    }

    /**
     * 빈 값/공백은 null(필터 미지정)으로, 그 외는 parseOrThrow 동작.
     * GET /faqs?topic=... 같은 optional 필터에 사용.
     */
    public static FaqTopic parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseOrThrow(value);
    }

    @Converter(autoApply = true)
    public static class FaqTopicConverter implements AttributeConverter<FaqTopic, String> {
        @Override
        public String convertToDatabaseColumn(FaqTopic topic) {
            return topic == null ? null : topic.getValue();
        }

        @Override
        public FaqTopic convertToEntityAttribute(String value) {
            return value == null ? null : FaqTopic.from(value);
        }
    }
}
