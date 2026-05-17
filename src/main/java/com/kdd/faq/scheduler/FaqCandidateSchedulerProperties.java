package com.kdd.faq.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FAQ 후보 인입 스케줄러 외부화 설정.
 * <p>
 * yml 키 prefix: {@code faq.candidate.scheduler}.
 * <ul>
 *     <li>{@code enabled} — 운영 외 환경(로컬/CI)에서 외부 AI 호출을 끄기 위한 토글</li>
 *     <li>{@code cron} — Spring cron 표현식 6필드(초·분·시·일·월·요일). 기본 매일 새벽 3시</li>
 *     <li>{@code lookback-days} — ChatMessage 추출 윈도우(일). 너무 짧으면 클러스터 부족, 너무 길면 stale</li>
 *     <li>{@code max-questions} — AI 페이로드 상한. ChatMessage 추출 limit으로도 적용</li>
 *     <li>{@code top-k} / {@code min-cluster-size} — AI 명세 §2 요청 파라미터 기본값(5, 2)</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "faq.candidate.scheduler")
@Getter
@Setter
public class FaqCandidateSchedulerProperties {

    private boolean enabled = true;
    private String cron = "0 0 3 * * *";
    private int lookbackDays = 7;
    private int maxQuestions = 5_000;
    private int topK = 5;
    private int minClusterSize = 2;
}
