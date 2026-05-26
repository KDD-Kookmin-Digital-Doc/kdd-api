-- 인기 문서 dedup의 read-then-write race 차단용 — 동일 (user, doc, 10분 버킷)에
-- 두 row가 들어가지 못하도록 unique 인덱스.
-- DocumentService.getDocumentDetail은 sliding 10분 윈도우 검사 후 INSERT 하는데,
-- 두 동시 요청이 모두 sliding=false를 읽으면 INSERT가 두 번 일어나면서 documents.view_count가 +2로 새는
-- drift가 발생한다. 본 인덱스가 두 번째 INSERT를 unique violation으로 막아 한 번만 카운트되도록 보장한다.
--
-- 버킷 식: date_bin('10 minutes', viewed_at, TIMESTAMP '2000-01-01')
-- — 2000-01-01을 origin으로 한 10분 단위 bin의 시작 시각을 반환. 같은 10분 윈도우는 같은 값.
--
-- IMMUTABLE 요구: PostgreSQL은 인덱스 표현식에 IMMUTABLE 함수만 허용한다.
-- date_bin(interval, timestamp, timestamp)은 PG 14+에서 IMMUTABLE로 안전하게 인덱스 표현식에 쓸 수 있다.
-- (참고: extract(epoch FROM timestamp)와 timestamp AT TIME ZONE 'UTC'는 모두 STABLE이라 사용 불가.)
--
-- 기존 row는 모두 다른 시각이므로 인덱스 생성 시 충돌 없음. lock_timeout은 V7 컨벤션과 동일하게 5s.
SET LOCAL lock_timeout = '5s';

CREATE UNIQUE INDEX uq_document_views_user_doc_10min
    ON document_views (
        document_id,
        user_id,
        (date_bin('10 minutes', viewed_at, TIMESTAMP '2000-01-01'))
    );
