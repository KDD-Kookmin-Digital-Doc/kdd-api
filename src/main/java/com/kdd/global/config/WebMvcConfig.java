package com.kdd.global.config;

import com.kdd.faq.entity.FaqTopic;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @RequestParam/@PathVariable에서 enum value(소문자)로 들어오는 값을 바인딩하기 위한 컨버터 등록.
 * Spring 기본 StringToEnumConverterFactory는 enum 상수명만 매칭하므로 별도 등록이 없으면
 * `?topic=academic` 같은 명세서 표준 호출이 enum NAME 매칭 실패로 깨진다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, FaqTopic.class, source ->
                (source == null || source.isBlank()) ? null : FaqTopic.from(source));
    }
}
