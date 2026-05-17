-- chat_sessions.source_type CHECK 제약에 'recommended' 추가.
-- 추천 질문 클릭으로 생성된 세션을 별도 source_type으로 표시해 FAQ 클러스터링 입력에서 제외하기 위함.
-- 기존 'normal'/'faq' row는 그대로 유지되며, 새 값 'recommended'가 허용 집합에 추가될 뿐이다.

ALTER TABLE chat_sessions DROP CONSTRAINT IF EXISTS chat_sessions_source_type_check;

ALTER TABLE chat_sessions
    ADD CONSTRAINT chat_sessions_source_type_check
    CHECK (source_type IN ('normal', 'faq', 'recommended'));
