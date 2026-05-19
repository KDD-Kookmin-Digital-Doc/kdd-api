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

    // 외부 AI 호출이 일어나므로 기본값은 비활성 — 운영(application-prod.yml)에서만 true로 오버라이드한다.
    // YAML 바인딩 누락/오타로 빈 값이 들어와도 로컬·CI에서 의도치 않은 AI 호출이 발생하지 않도록 한다.
    private boolean enabled = false;
    private String cron = "0 0 3 * * *";
    private int lookbackDays = 7;
    private int maxQuestions = 5_000;
    private int topK = 5;
    private int minClusterSize = 2;
}
