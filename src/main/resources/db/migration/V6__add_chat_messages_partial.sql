-- done 이벤트 없이 AI 스트림이 끊긴 경우 누적된 부분 답변임을 표시하는 플래그.
-- FE는 이 값으로 메시지를 다르게 렌더링(연한 색·"중단됨" 배지 등)할 수 있고, 운영/통계는 단순 BOOLEAN 필터로 분리 가능.
-- 본문 안에 한국어 suffix를 박지 않기 위함이라 default false로 기존 행은 모두 정상 메시지로 본다.
ALTER TABLE chat_messages
    ADD COLUMN partial BOOLEAN NOT NULL DEFAULT false;
