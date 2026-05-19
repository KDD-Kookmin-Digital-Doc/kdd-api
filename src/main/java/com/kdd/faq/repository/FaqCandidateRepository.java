package com.kdd.faq.repository;

import com.kdd.faq.entity.FaqCandidate;
import com.kdd.faq.entity.FaqCandidateStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FaqCandidateRepository extends JpaRepository<FaqCandidate, Long> {

    /**
     * 관리자 후보 목록 조회용. status 필터 기반으로 ERD 인덱스(status, ...)를 활용한다.
     * REJECTED 후보도 row는 보존되지만 일반 목록(PENDING)에 노출되지 않도록 service가 PENDING으로 호출한다.
     */
    Page<FaqCandidate> findByStatus(FaqCandidateStatus status, Pageable pageable);

    /**
     * 채팅 시작 전 추천 질문 노출용 — 가장 최근 인입된 PENDING 후보를 frequency 내림차순으로 N개 반환.
     * 명세서 §"채팅 시작 전 추천 질문 조회"의 "TOP 5" 데이터 소스로 동일 후보 row를 재사용한다
     * (별도 캐시 테이블 없이 단일 source of truth 유지).
     * frequency가 동일하면 최신 createdAt 우선 — 같은 인입 주기 내 동률 시 인입 순서 유지.
     */
    @Query("""
            select c
            from FaqCandidate c
            where c.status = com.kdd.faq.entity.FaqCandidateStatus.PENDING
            order by c.frequency desc, c.createdAt desc, c.id desc
            """)
    List<FaqCandidate> findTopRecommended(Pageable pageable);

    /**
     * 승인/반려용 row-level write lock 조회.
     * <p>
     * 다수 관리자가 같은 후보를 동시에 승인/반려할 때 race condition으로 FAQ가 중복 등록되거나
     * 상태가 마지막 commit으로 덮어 써지는 것을 막는다. PESSIMISTIC_WRITE는 PostgreSQL의 FOR UPDATE
     * 로 변환되어 동일 row에 대한 다른 트랜잭션을 직렬화한다.
     * <p>
     * 락 timeout은 application.yml의 hikari connection-init-sql(`SET lock_timeout='5s'`)이 세션 GUC로
     * 모든 커넥션에 일괄 적용한다. Hibernate `@QueryHint(jakarta.persistence.lock.timeout)`은
     * PostgreSQLDialect.supportsWait()=false라 silent no-op이라 사용하지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FaqCandidate c where c.id = :id")
    Optional<FaqCandidate> findByIdForUpdate(@Param("id") Long id);
}
