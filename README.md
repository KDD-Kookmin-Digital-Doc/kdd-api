# KDD Backend API

> **KDD (Kookmin Digital Doc)** — 국민대학교 학칙·학사규정 RAG 챗봇 백엔드
>
> Spring Boot 3.4.3 / Java 17 / PostgreSQL 16 / Redis 7

## 로컬 실행

```bash
# 1. 인프라
docker compose up -d postgres redis

# 2. 환경변수
cp .env.example .env

# 3. 서버
./gradlew bootRun
# http://localhost:8000
```

## EC2 배포

```bash
docker compose up --build -d
```
