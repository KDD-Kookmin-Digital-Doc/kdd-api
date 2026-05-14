package com.kdd.faq.repository;

import com.kdd.faq.entity.FaqCandidate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

// 페이지네이션 조회는 JpaRepository가 제공하는 findAll(Pageable) 그대로 사용한다.
public interface FaqCandidateRepository extends JpaRepository<FaqCandidate, Long> {

    /**
     * 승인/반려용 row-level write lock 조회.
     * <p>
     * 다수 관리자가 같은 후보를 동시에 승인/반려할 때 race condition으로 FAQ가 중복 등록되거나
     * 상태가 마지막 commit으로 덮어 써지는 것을 막는다. PESSIMISTIC_WRITE는 PostgreSQL의 FOR UPDATE
     * 로 변환되어 동일 row에 대한 다른 트랜잭션을 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FaqCandidate c where c.id = :id")
    Optional<FaqCandidate> findByIdForUpdate(Long id);
}
