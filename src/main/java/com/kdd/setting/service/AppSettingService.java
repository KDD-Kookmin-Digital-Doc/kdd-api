package com.kdd.setting.service;

import com.kdd.setting.entity.AppSetting;
import com.kdd.setting.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingService {

    public static final String KEY_DEFAULT_CHAT_LIMIT = "default_chat_limit";

    // V4 시드와 동일 — DB row가 어떤 이유로든 비어 있을 때만 사용되는 안전망
    private static final int FALLBACK_DEFAULT_CHAT_LIMIT = 20;

    private final AppSettingRepository appSettingRepository;

    /**
     * 신규 가입자에게 적용되는 일일 채팅 한도. DB의 값이 비어 있거나, 숫자가 아니거나, 음수면
     * {@link #FALLBACK_DEFAULT_CHAT_LIMIT}로 대체한다. 음수 방어가 없으면 신규 가입 시
     * {@code users_daily_chat_limit_check} 제약에 걸려 가입 자체가 실패할 수 있다.
     */
    @Transactional(readOnly = true)
    public int getDefaultChatLimit() {
        int value = appSettingRepository.findById(KEY_DEFAULT_CHAT_LIMIT)
                .map(s -> parseIntOrFallback(s.getValue()))
                .orElse(FALLBACK_DEFAULT_CHAT_LIMIT);
        if (value < 0) {
            log.warn("default_chat_limit stored as negative ({}), using fallback {}",
                    value, FALLBACK_DEFAULT_CHAT_LIMIT);
            return FALLBACK_DEFAULT_CHAT_LIMIT;
        }
        return value;
    }

    /**
     * 컨트롤러의 {@code @Min(0)}이 1차로 음수를 차단하지만, 다른 진입점(테스트/스크립트/향후 신규 컨트롤러)에서
     * 우회 호출될 가능성에 대비해 서비스 레이어에서도 방어한다. 음수를 그대로 저장하면 DB에는 잘못된 값이 남고
     * 읽기 경로({@link #getDefaultChatLimit()})는 fallback으로 대체해 응답하므로 DB값과 런타임값이 어긋난다.
     */
    @Transactional
    public int updateDefaultChatLimit(int newLimit) {
        if (newLimit < 0) {
            throw new IllegalArgumentException("default_chat_limit must be >= 0");
        }
        String value = String.valueOf(newLimit);
        appSettingRepository.findById(KEY_DEFAULT_CHAT_LIMIT)
                .ifPresentOrElse(
                        setting -> setting.updateValue(value),
                        () -> appSettingRepository.save(AppSetting.builder()
                                .key(KEY_DEFAULT_CHAT_LIMIT)
                                .value(value)
                                .description("신규 가입자에게 적용되는 일일 채팅 한도")
                                .build())
                );
        return newLimit;
    }

    private int parseIntOrFallback(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // DB에 손상된 값이 들어간 케이스를 운영 모니터링에서 감지할 수 있도록 명시적으로 경고 로그
            log.warn("Failed to parse default_chat_limit value '{}', using fallback {}",
                    value, FALLBACK_DEFAULT_CHAT_LIMIT);
            return FALLBACK_DEFAULT_CHAT_LIMIT;
        }
    }
}
