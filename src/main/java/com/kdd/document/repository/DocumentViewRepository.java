package com.kdd.document.repository;

import com.kdd.document.entity.DocumentView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * document_views 기본 CRUD. 인기 문서 집계 쿼리는 {@link DocumentRepository#findPopularDocuments}가
 * LEFT JOIN으로 직접 수행하므로 본 Repository는 insert + 10분 dedup 체크만 담당한다.
 */
public interface DocumentViewRepository extends JpaRepository<DocumentView, Long> {

    /**
     * 요구사항 3-(2)-5 "동일 사용자+문서 + 일정 시간(예: 10분) 내 중복 조회는 1회로 집계" 정책 구현용.
     * <p>
     * Native query로 DB의 {@code now() - interval '10 minutes'}와 비교한다.
     * Java {@code LocalDateTime.now()}는 JVM 기본 TZ를 따르고 INSERT의 {@code now()}는 PG 세션 TZ를
     * 따르므로 두 시계가 다르면 슬라이딩 윈도우가 어긋난다 (컨테이너 UTC vs PG Asia/Seoul 시 9시간 drift).
     * INSERT와 동일한 DB 시계를 사용해 TZ-mismatch 회귀를 차단한다.
     * <p>
     * minutes 파라미터로 윈도우 폭을 받아 호출 측({@link com.kdd.document.service.DocumentService#VIEW_DEDUP_MINUTES})의
     * 정책 변경을 한 곳에 유지한다.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM document_views
                WHERE document_id = :documentId
                  AND user_id = :userId
                  AND viewed_at >= now() - make_interval(mins => :minutes)
            )
            """, nativeQuery = true)
    boolean existsWithinWindow(@Param("documentId") Long documentId,
                                @Param("userId") Long userId,
                                @Param("minutes") int minutes);

    /**
     * V10 unique 인덱스(document_id, user_id, 10분 epoch 버킷) 기반의 race-safe INSERT.
     * <p>
     * read-then-write race(두 동시 요청이 모두 {@link #existsWithinWindow}에서 false를 본 뒤 INSERT) 시
     * Hibernate가 ConstraintViolationException을 던지면 PostgreSQL TX가 aborted 상태가 되어
     * 후속 쿼리가 모두 실패한다. ON CONFLICT DO NOTHING은 DB가 충돌을 swallow하므로 TX 상태를 깨지 않는다.
     * <p>
     * 반환값: 실제 INSERT된 row 수 (0 또는 1). 0이면 동시 요청에 의해 이미 처리된 경우.
     */
    @Modifying
    @Query(value = """
            INSERT INTO document_views (document_id, user_id, viewed_at)
            VALUES (:documentId, :userId, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("documentId") Long documentId,
                       @Param("userId") Long userId);
}
