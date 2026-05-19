-- ALTER TABLE ADD COLUMN with default is metadata-only in PG11+, but we still cap lock wait
-- to surface conflicting long-running queries quickly instead of starving the connection pool.
SET LOCAL lock_timeout = '5s';

-- AI 서버(/api/faq/analyze) 응답에 포함된 frequency(클러스터 빈도)를 후보에 저장한다.
-- 관리자 검토 화면에서 "이 질문이 몇 건의 사용자 질문에서 클러스터링됐는지"를 보여줘
-- 승인 우선순위 판단 근거로 활용한다 (BE-AI 명세 응답 candidates[].frequency 그대로 매핑).
-- 기존 row(스케줄러 인입 이전 수동 시드 가능성 포함)에 대해서는 0으로 채워 NOT NULL 제약을 만족시킨다.
ALTER TABLE faq_candidates
    ADD COLUMN frequency INTEGER NOT NULL DEFAULT 0;

-- frequency가 음수일 수 없다는 도메인 invariant — AI 응답 변조나 잘못된 시드 시 DB 레벨에서 차단.
ALTER TABLE faq_candidates
    ADD CONSTRAINT faq_candidates_frequency_nonneg CHECK (frequency >= 0);
