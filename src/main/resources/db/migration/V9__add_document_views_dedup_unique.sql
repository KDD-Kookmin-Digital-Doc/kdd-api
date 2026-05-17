-- 인기 문서 dedup의 read-then-write race 차단용 — 동일 (user, doc, 10분 epoch 버킷)에
-- 두 row가 들어가지 못하도록 unique 인덱스.
-- DocumentService.getDocumentDetail은 sliding 10분 윈도우 검사 후 INSERT 하는데,
-- 두 동시 요청이 모두 sliding=false를 읽으면 INSERT가 두 번 일어나면서 documents.view_count가 +2로 새는
-- drift가 발생한다. 본 인덱스가 두 번째 INSERT를 unique violation으로 막아 한 번만 카운트되도록 보장한다.
--
-- 버킷 식: floor(epoch_seconds / 600) — 10분(600초) 단위로 UTC 기준 정렬.
-- 동일 (user, doc) 페어가 같은 10분 버킷에 들어오면 두 번째 INSERT는 DataIntegrityViolationException으로 실패.
-- 호출부는 이 예외를 잡아 view_count 증가도 함께 건너뛴다.
--
-- IMMUTABLE 요구: PostgreSQL은 인덱스 표현식에 IMMUTABLE 함수만 허용한다.
-- extract(epoch FROM timestamp without time zone)은 세션 TimeZone에 의존해 STABLE이라 인덱스 표현식으로 거부된다.
-- viewed_at을 'UTC'로 캐스팅해 timestamptz로 변환하면 extract(epoch ...)이 IMMUTABLE이 된다.
-- viewed_at은 CreationTimestamp(서버 now())로 채워지므로 UTC 해석이 안전.
--
-- 기존 row는 모두 다른 시각이므로 인덱스 생성 시 충돌 없음. lock_timeout은 V7 컨벤션과 동일하게 5s.
SET LOCAL lock_timeout = '5s';

CREATE UNIQUE INDEX uq_document_views_user_doc_10min
    ON document_views (
        document_id,
        user_id,
        (floor(extract(epoch FROM (viewed_at AT TIME ZONE 'UTC')) / 600)::bigint)
    );
