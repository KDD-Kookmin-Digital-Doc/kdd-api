package com.kdd.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 기반 잡 실행을 활성화한다.
 *
 * 사용처:
 * - {@code AuthSessionCleanupJob} — 만료/revoked 세션 일일 정리.
 * - {@code FaqCandidateScheduler} — FAQ 후보 자동 인입 (외부 AI 호출, application.yml로 토글).
 *
 * 풀 크기는 application.yml의 {@code spring.task.scheduling.pool.size}로 조정 가능.
 * 단일 인스턴스(Lightsail 단일 컨테이너) 배포 가정이므로 분산 락은 사용하지 않는다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
