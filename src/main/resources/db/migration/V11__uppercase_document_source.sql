-- documents.source 를 lowercase("sw","kmu") → uppercase("SW","KMU")로 정렬
-- 배경: 노션 API 명세(2026-05-17)가 대문자 리터럴이며 FE 타입도 이미 "SW"|"KMU".
-- 이전 V2에서 lowercase로 통일했으나(#60 round-trip 이슈), 명세 정합을 위해 대문자로 되돌림.
-- round-trip은 enum value도 함께 "SW"/"KMU"로 변경하여 유지된다.

ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_source_check;

UPDATE documents SET source = upper(source) WHERE source <> upper(source);

ALTER TABLE documents
    ADD CONSTRAINT documents_source_check CHECK (source IN ('SW', 'KMU'));
