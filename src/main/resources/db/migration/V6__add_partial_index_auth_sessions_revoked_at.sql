-- auth_sessions cleanup 잡(#69)이 사용하는 DELETE 술어는
--   WHERE revoked_at IS NOT NULL OR expires_at < :cutoff
-- 인데 revoked_at 인덱스가 없어 OR 분기에서 seq scan이 발생한다.
-- 정상 운영 중인 세션은 revoked_at IS NULL이라 그 분기는 평시 0건이므로,
-- 부분 인덱스로 revoke된 행만 색인해 인덱스 크기를 최소로 유지한다.
--
-- CREATE INDEX는 기본적으로 ACCESS EXCLUSIVE LOCK을 잡지만, CONCURRENTLY로 만들면
-- shared lock만 잡아 운영 중 INSERT/UPDATE를 막지 않는다. Flyway에서 CONCURRENTLY를
-- 쓰려면 트랜잭션 외부에서 실행해야 하므로 이 마이그레이션 파일에는 단일 statement만 둔다.
-- (Flyway는 PostgreSQL에서 CREATE INDEX CONCURRENTLY 단일 statement 파일을 자동으로
--  트랜잭션 없이 실행한다.)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_auth_session_revoked_at
    ON auth_sessions (revoked_at)
    WHERE revoked_at IS NOT NULL;
