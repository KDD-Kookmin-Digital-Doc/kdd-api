package com.kdd.setting.service;

import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.setting.entity.AppSetting;
import com.kdd.setting.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppSettingService {

    public static final String KEY_DEFAULT_CHAT_LIMIT = "default_chat_limit";

    // V4 시드와 동일 — DB row가 어떤 이유로든 비어 있을 때만 사용되는 안전망
    private static final int FALLBACK_DEFAULT_CHAT_LIMIT = 20;

    private final AppSettingRepository appSettingRepository;

    @Transactional(readOnly = true)
    public int getDefaultChatLimit() {
        return appSettingRepository.findById(KEY_DEFAULT_CHAT_LIMIT)
                .map(s -> parseIntOrFallback(s.getValue()))
                .orElse(FALLBACK_DEFAULT_CHAT_LIMIT);
    }

    @Transactional
    public int updateDefaultChatLimit(int newLimit) {
        if (newLimit < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
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
            return FALLBACK_DEFAULT_CHAT_LIMIT;
        }
    }
}
