package com.kdd.chat.repository;

import com.kdd.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // AI 호출 히스토리용. partial=true (스트림 중단으로 잘린 답변, #55) 는 제외해 AI 가 자기 잘린 출력을
    // 컨텍스트로 다시 받지 않게 한다 (#86). UI 표시용 히스토리는 partial 을 그대로 포함해야 하므로 별도 메서드.
    List<ChatMessage> findTop10BySessionIdAndPartialFalseOrderByCreatedAtDescIdDesc(Long sessionId);

    boolean existsBySessionId(Long sessionId);

    /**
     * FAQ 후보 인입 스케줄러용 — 지정 시각 이후의 사용자(role='user') 질문 텍스트를 최신순으로 반환한다.
     * <p>
     * AI 서버에 questions 배열로 전달할 입력을 만들기 위한 쿼리. content만 select하여 본 엔티티
     * 로딩/소스 N+1을 피한다. 한 주기에 너무 많은 row를 끌어오면 AI 서버 페이로드/메모리 부담이 커지므로
     * 호출부에서 {@link Pageable}로 상한(예: 5000건)을 적용한다.
     * <p>
     * source_type='normal' 세션만 포함한다 — FAQ 클릭/추천 클릭으로 자동 생성된 세션(faq/recommended)을
     * 클러스터링 입력에 다시 넣으면 "FAQ로 만든 질문 → 클릭 → 다음 클러스터링 → 또 FAQ"의 피드백 루프가
     * 형성되어 인기 질문 분포가 왜곡된다.
     * <p>
     * role 컬럼은 DB에 소문자 'user'/'assistant' 문자열로 저장되며 {@code MessageRole} converter를
     * 거치므로 JPQL 비교 시 enum 상수를 그대로 사용하면 된다.
     */
    @Query("""
            select cm.content
            from ChatMessage cm
            where cm.role = com.kdd.chat.entity.MessageRole.USER
              and cm.session.sourceType = com.kdd.chat.entity.SourceType.NORMAL
              and cm.createdAt >= :since
            order by cm.createdAt desc, cm.id desc
            """)
    List<String> findRecentUserQuestionContents(@Param("since") LocalDateTime since, Pageable pageable);
}
