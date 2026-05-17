package com.kdd.statistics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 통계 산정 전용 native query 모음.
 * <p>
 * 통계는 여러 테이블(chat_messages, chat_message_sources, documents, document_categories, users, chat_sessions)을
 * 가로지르는 read-only 집계이므로 도메인별 Repository에 분산시키지 않고 본 클래스에 모은다.
 * 모든 메서드는 호출자가 readOnly 트랜잭션 안에서 실행하는 것을 전제로 한다.
 */
@Repository
public class StatisticsRepository {

    @PersistenceContext
    private EntityManager em;

    /** 전체 사용자 질문 수 (chat_messages WHERE role='user'). */
    public long countUserMessages() {
        Object result = em.createNativeQuery(
                "SELECT COUNT(*) FROM chat_messages WHERE role = 'user'"
        ).getSingleResult();
        return ((Number) result).longValue();
    }

    /** 활성 문서 수 (deleted_at IS NULL). */
    public long countActiveDocuments() {
        Object result = em.createNativeQuery(
                "SELECT COUNT(*) FROM documents WHERE deleted_at IS NULL"
        ).getSingleResult();
        return ((Number) result).longValue();
    }

    /**
     * 카테고리별 질문 참조 수.
     * 채팅 답변(ASSISTANT 메시지)의 출처 문서들을 카테고리별로 단순 덧셈 — 한 답변이 같은 카테고리 문서를
     * 여러 번 인용하면 카테고리 카운트도 그만큼 증가한다 (팀 합의 — 단순 덧셈).
     * 출처가 없는 메시지(잡담/fallback)는 자동으로 집계에서 제외된다 (LEFT JOIN이 아닌 INNER 의도).
     * <p>
     * soft-delete된 문서(documents.deleted_at IS NOT NULL)도 의도적으로 포함한다 — 통계는 "참조가
     * 발생한 시점의 사실"을 보여주는 것이 자연스럽고, 문서 삭제로 인해 과거 사용량이 사라지면 추세를
     * 왜곡한다. countActiveDocuments()는 별도로 현재 활성 문서 수를 보여주므로 두 지표는 분리된다.
     *
     * @return [categoryName, questionCount] 튜플의 List, questionCount 내림차순
     */
    @SuppressWarnings("unchecked")
    public List<CategoryCount> aggregateQuestionsByCategory() {
        // role='assistant' 필터는 현재 RAG 파이프라인이 assistant 메시지에만 source를 attach하므로
        // 실질적으로는 no-op이지만, 향후 user 메시지에 첨부파일 source가 생기는 회귀를 방어한다.
        List<Object[]> rows = em.createNativeQuery("""
                SELECT dc.name AS category, COUNT(*) AS question_count
                FROM chat_message_sources cms
                JOIN chat_messages cm ON cm.id = cms.message_id AND cm.role = 'assistant'
                JOIN documents d ON d.id = cms.document_id
                JOIN document_categories dc ON dc.id = d.category_id
                GROUP BY dc.id, dc.name
                ORDER BY question_count DESC, dc.name ASC
                """).getResultList();

        return rows.stream()
                .map(row -> new CategoryCount(
                        (String) row[0],
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    // ---------------------------------------------------------------------
    // 사용자 인원 통계 (메시지 수가 아닌 "사람 수").
    // 명세 §"통계 조회" - users 섹션. 메시지 수와 분리된 별도 집계로, 한 학생이 여러 질문을 보내도 1명으로 계산.
    // ---------------------------------------------------------------------

    /**
     * 전체 사용자 수 — 시연·운영 관리 계정(role='admin')과 비활성 계정(is_active=false), 그리고
     * 프로필 미완료 사용자(is_profile_completed=false)는 제외.
     * <p>
     * is_profile_completed=true 필터가 중요한 이유: byUserType/byDepartment/byGrade는 student_profiles
     * 또는 user_type 필드에 의존하므로 프로필 미완료 사용자가 자동으로 누락된다. totalUsers에도 동일한
     * 필터를 걸어야 응답 내 합계 정합성(totalUsers == byUserType.sum())이 유지된다.
     */
    public long countTotalUsers() {
        Object result = em.createNativeQuery(
                "SELECT COUNT(*) FROM users WHERE role = 'user' AND is_active = true AND is_profile_completed = true"
        ).getSingleResult();
        return ((Number) result).longValue();
    }

    /**
     * 사용자 유형별 인원 수 (학생/교직원). user_type은 'student'/'staff' 두 값.
     * 프로필 미완료 사용자는 user_type이 null일 수 있으므로 is_profile_completed=true로 필터해
     * totalUsers와 합 정합성을 유지한다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Long> countUsersByUserType() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT u.user_type AS user_type, COUNT(*) AS user_count
                FROM users u
                WHERE u.role = 'user' AND u.is_active = true AND u.is_profile_completed = true
                GROUP BY u.user_type
                """).getResultList();

        return rows.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).longValue()
        ));
    }

    /**
     * 학생 학과별 인원 수 (software/ai). 교직원은 student_profiles에 없으므로 자동 제외.
     * admin/비활성/프로필 미완료 user의 student_profile(존재한다면)도 통계에서 빼기 위해 users JOIN으로 필터.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Long> countStudentsByDepartment() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT sp.department AS department, COUNT(*) AS user_count
                FROM student_profiles sp
                JOIN users u ON u.id = sp.user_id
                WHERE u.role = 'user' AND u.is_active = true AND u.is_profile_completed = true
                GROUP BY sp.department
                """).getResultList();

        return rows.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).longValue()
        ));
    }

    /**
     * 학생 학년별 인원 수.
     * <p>
     * 1학년 미만(0/음수 회귀)은 '1' 버킷으로 클램프, 5학년 이상은 '5_or_above'로 누적해
     * Service 측 getOrDefault 키('1'~'4','5_or_above') 외 버킷이 만들어져 row가 silent하게
     * 사라지는 일이 없도록 한다 (DTO 검증 누락에 대한 보호선).
     * <p>
     * admin/비활성/프로필 미완료 user는 분포에서 제외 — totalUsers와의 정합성 유지.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Long> countStudentsByGrade() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CASE
                           WHEN sp.grade <= 1 THEN '1'
                           WHEN sp.grade >= 5 THEN '5_or_above'
                           ELSE CAST(sp.grade AS TEXT)
                       END AS grade_bucket,
                       COUNT(*) AS user_count
                FROM student_profiles sp
                JOIN users u ON u.id = sp.user_id
                WHERE u.role = 'user' AND u.is_active = true AND u.is_profile_completed = true
                GROUP BY grade_bucket
                """).getResultList();

        return rows.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).longValue()
        ));
    }

    /** 누적 채팅 세션 수 (chat_sessions 테이블 전체 row 수). */
    public long countTotalSessions() {
        Object result = em.createNativeQuery(
                "SELECT COUNT(*) FROM chat_sessions"
        ).getSingleResult();
        return ((Number) result).longValue();
    }

    public record CategoryCount(String category, long questionCount) {}
}
