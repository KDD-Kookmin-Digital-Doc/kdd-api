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

    /**
     * 사용자 유형별 사용자 질문 수.
     * chat_messages(role='user') → chat_sessions → users 조인 후 user_type 으로 group.
     * 결과는 user_type → count 맵으로 반환 (없는 타입은 호출자가 0으로 보충).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Long> countUserMessagesByUserType() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT u.user_type AS user_type, COUNT(*) AS msg_count
                FROM chat_messages cm
                JOIN chat_sessions cs ON cs.id = cm.session_id
                JOIN users u ON u.id = cs.user_id
                WHERE cm.role = 'user'
                GROUP BY u.user_type
                """).getResultList();

        return rows.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).longValue()
        ));
    }

    public record CategoryCount(String category, long questionCount) {}
}
