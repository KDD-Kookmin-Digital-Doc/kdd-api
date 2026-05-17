package com.kdd.user.repository;

import com.kdd.user.entity.Role;
import com.kdd.user.entity.User;
import com.kdd.user.entity.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    // admin 사용자 목록 — userType/role 필터와 name/email 부분검색을 옵셔널로 받는다.
    // 빈 문자열은 컨트롤러에서 null로 치환되어 들어온다 (필터 미적용).
    // CAST(:search AS string): Hibernate 6이 null 파라미터 타입을 bytea로 추론해
    // PostgreSQL이 LOWER(bytea) 함수를 못 찾는 #82 회피.
    @Query("""
            SELECT u FROM User u
            WHERE (:userType IS NULL OR u.userType = :userType)
              AND (:role IS NULL OR u.role = :role)
              AND (:search IS NULL
                   OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """)
    Page<User> searchForAdmin(@Param("userType") UserType userType,
                              @Param("role") Role role,
                              @Param("search") String search,
                              Pageable pageable);

    // 일괄 한도 변경 — 엔티티를 메모리에 로드하지 않고 DB에서 한 번의 UPDATE로 처리.
    // 수천 명 변경에도 트랜잭션이 짧고 메모리/lock 부담이 낮다. updated row 수를 반환해
    // 존재하지 않는 ID 누락을 호출자가 응답으로 알릴 수 있게 한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.dailyChatLimit = :dailyChatLimit WHERE u.id IN :userIds")
    int updateDailyChatLimitByIds(@Param("userIds") List<Long> userIds,
                                   @Param("dailyChatLimit") int dailyChatLimit);
}
