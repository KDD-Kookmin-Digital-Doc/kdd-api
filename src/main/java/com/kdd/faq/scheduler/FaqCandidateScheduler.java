package com.kdd.faq.scheduler;

import com.kdd.ai.client.AiServerClient;
import com.kdd.ai.dto.AiFaqAnalyzeRequest;
import com.kdd.ai.dto.AiFaqAnalyzeResponse;
import com.kdd.ai.exception.AiServerException;
import com.kdd.chat.repository.ChatMessageRepository;
import com.kdd.faq.service.FaqCandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FAQ 후보 자동 인입 스케줄러 — BE-AI 명세 §2 (인기 질문 TOP 5 & FAQ 후보 생성) 흐름의 트리거.
 * <p>
 * 매 주기마다 다음 단계를 수행한다.
 * <ol>
 *     <li>최근 {@code lookback-days}일 이내 사용자 질문(role='user', source_type='normal') 텍스트를
 *         {@code max-questions}건 추출</li>
 *     <li>AI 서버 {@code POST /api/faq/analyze}에 전달 — top_k, min_cluster_size 함께 전송</li>
 *     <li>응답 candidates를 {@link FaqCandidateService#ingestFromAi(AiFaqAnalyzeResponse)}로 위임 — 요구사항
 *         4-(3)-1에 따라 중복 검사 없이 모든 항목을 신규 후보로 batch insert</li>
 * </ol>
 * 실패 시나리오는 모두 catch — 스케줄러는 다음 주기에 재시도하면 되므로 한 회차 실패가 애플리케이션을 멈추거나
 * 다음 주기를 막아서는 안 된다. 로그 레벨은 원인별로 분리한다:
 * <ul>
 *     <li>{@link AiServerException} (AI 서버 일시 장애·네트워크 단절): <b>warn</b> — 예상되는 재시도 가능 오류로
 *         운영 모니터링은 {@code AiServerClient}의 ERROR 로그에서 별도로 확인한다.</li>
 *     <li>그 외 예상치 못한 {@link Exception} (DB 일시 장애·코드 버그 등): <b>error</b> — 원인 파악이 필요한
 *         신호이므로 알람 대상으로 끌어올린다.</li>
 * </ul>
 * 어느 쪽이든 catch 후 정상 종료해 다음 주기 재시도를 보장한다.
 * <p>
 * <b>단일 인스턴스 가정 (Lightsail 단일 컨테이너 배포):</b>
 * 다중 인스턴스 운영 시 같은 cron tick에 여러 워커가 동시에 AI 서버를 호출해 중복 후보가 batch insert 될 수 있다.
 * 운영은 단일 컨테이너로만 굴리며, 향후 수평 확장 시 분산 락(예: ShedLock)을 도입해야 한다 —
 * {@link com.kdd.global.config.SchedulingConfig} 참조.
 * <p>
 * 스케줄러 실행 자체는 {@code faq.candidate.scheduler.enabled} 토글로 끌 수 있어, 로컬 개발/CI에서 외부 호출을 차단할 수 있다.
 * 기본값은 false이며 운영 프로파일(application-prod.yml)에서 true로 오버라이드한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqCandidateScheduler {

    private final FaqCandidateSchedulerProperties properties;
    private final ChatMessageRepository chatMessageRepository;
    private final AiServerClient aiServerClient;
    private final FaqCandidateService faqCandidateService;

    /**
     * cron 표현식은 application.yml의 {@code faq.candidate.scheduler.cron}로 외부화.
     * Spring 6 cron은 6필드(초·분·시·일·월·요일)이며 기본값은 매일 새벽 3시 (서버 timezone 기준).
     * <p>
     * 메서드는 @Transactional 미부착 — 인입 트랜잭션은 ingest 호출 안에서 닫힌다.
     * 추출 read는 별도 짧은 트랜잭션이 자동 적용되어, 외부 AI 호출이 DB 커넥션을 점유하는 시간을 최소화한다.
     */
    @Scheduled(cron = "${faq.candidate.scheduler.cron:0 0 3 * * *}")
    public void run() {
        if (!properties.isEnabled()) {
            log.debug("[FAQ-Scheduler] disabled by config — skip");
            return;
        }

        long startedAt = System.currentTimeMillis();
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(properties.getLookbackDays());
            List<String> questions = chatMessageRepository.findRecentUserQuestionContents(
                    since, PageRequest.of(0, properties.getMaxQuestions()));

            if (questions.isEmpty()) {
                log.info("[FAQ-Scheduler] no recent user questions in last {}d — skip AI call",
                        properties.getLookbackDays());
                return;
            }

            AiFaqAnalyzeRequest request = new AiFaqAnalyzeRequest(
                    questions, properties.getTopK(), properties.getMinClusterSize());
            AiFaqAnalyzeResponse response = aiServerClient.analyzeFaq(request);

            int inserted = faqCandidateService.ingestFromAi(response);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("[FAQ-Scheduler] done — questions={}, inserted={}, elapsed={}ms",
                    questions.size(), inserted, elapsedMs);
        } catch (AiServerException e) {
            // AI 서버 일시 장애는 다음 주기 재시도. 운영 모니터링은 AiServerClient의 ERROR 로그에서 확인.
            log.warn("[FAQ-Scheduler] AI server error — will retry next cycle", e);
        } catch (Exception e) {
            // DB 일시 장애·기타 예외도 동일 정책. 스케줄러가 죽지 않도록 광범위 catch.
            log.error("[FAQ-Scheduler] unexpected error — will retry next cycle", e);
        }
    }
}
