package com.kdd.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 기반 잡 실행을 활성화한다. 현재 사용처: auth_sessions 정리 잡 ({@code AuthSessionCleanupJob}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
