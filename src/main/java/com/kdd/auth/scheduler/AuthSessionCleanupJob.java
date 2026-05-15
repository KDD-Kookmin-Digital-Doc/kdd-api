package com.kdd.auth.scheduler;

import com.kdd.auth.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 모든 로그인/refresh가 auth_sessions에 행을 INSERT만 하고 정리하지 않아 테이블이 무한 증가한다.
 * 일일 스케줄로 revoke된 세션과 만료 후 보존 기간이 지난 세션을 삭제한다 (#69).
 *
 * 누적 잔존 행이 많은 첫 실행에서 한 번에 통째로 DELETE하면 lock 보유 시간과 WAL 부하가 커지므로,
 * 작은 배치를 별도 트랜잭션으로 반복 commit한다. 한 번의 실행에서 잡지 못한 잔여분은 다음 날 잡힌다.
 *
 * <p><b>배포 가정 — 단일 인스턴스</b>:
 * 현재 운영 배포는 단일 컨테이너(AWS Lightsail) 기준이라 {@link Scheduled}의 cluster-unaware
 * 특성이 문제되지 않는다. 향후 수평 확장으로 인스턴스가 N개가 되면 같은 시각에 N번 동시에
 * 잡이 돌아 {@code DELETE ... WHERE id IN (SELECT ... LIMIT)} 쿼리들이 같은 행 묶음을 두고
 * 경합한다. 정합성 자체는 손상되지 않지만(idempotent DELETE), 불필요한 lock 충돌이 생기므로
 * 그 시점에는 ShedLock 같은 분산 lock으로 단일 실행을 보장해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSessionCleanupJob {

    /** 한 트랜잭션에서 삭제할 최대 행 수. PG에서 무난한 작은 단위. */
    private static final int BATCH_SIZE = 1000;

    /** 한 실행에서 돌릴 최대 배치 수 — 폭주 방지 안전 캡(BATCH_SIZE * MAX_BATCHES = 1M 행). */
    private static final int MAX_BATCHES_PER_RUN = 1000;

    private final AuthSessionRepository authSessionRepository;

    @Value("${app.auth.session-cleanup.expired-retention-days}")
    private long expiredRetentionDays;

    @Scheduled(cron = "${app.auth.session-cleanup.cron}", zone = "Asia/Seoul")
    public void cleanup() {
        LocalDateTime expiredCutoff = LocalDateTime.now().minusDays(expiredRetentionDays);

        int totalDeleted = 0;
        int batches = 0;
        while (batches < MAX_BATCHES_PER_RUN) {
            int deleted = authSessionRepository.deleteRevokedOrExpiredBatch(expiredCutoff, BATCH_SIZE);
            totalDeleted += deleted;
            batches++;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }

        if (batches >= MAX_BATCHES_PER_RUN) {
            log.warn("auth_sessions cleanup hit batch cap: deleted={}, batches={}, expiredCutoff={} — remaining rows will be cleared on next run",
                    totalDeleted, batches, expiredCutoff);
        } else {
            log.info("auth_sessions cleanup completed: {} row(s) deleted in {} batch(es) (expiredCutoff={})",
                    totalDeleted, batches, expiredCutoff);
        }
    }
}
