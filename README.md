# KDD Backend API

> **KDD (Kookmin Digital Doc)** — 국민대학교 학칙·학사규정 RAG 챗봇 백엔드
>
> Spring Boot 3.4.3 / Java 17 / PostgreSQL 16

## 프로젝트 구조

> 추후 작성 예정

## 로컬 실행

```bash
# 1. 환경변수 설정
cp .env.example .env
# GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, JWT_SECRET 값을 채워넣으세요
# JWT_SECRET 생성: openssl rand -hex 32

# 2. 인프라 (DB)
docker compose up -d postgres

# 3. 서버
./gradlew bootRun
# http://localhost:8000
```

## API 문서 (Swagger)

서버 실행 후 아래 주소에서 API를 확인하고 테스트할 수 있습니다.

- Swagger UI: `http://localhost:8000/swagger-ui/index.html`

## EC2 배포

```bash
# 1. 환경변수 설정 (.env에 실제 값 입력)
cp .env.example .env

# 2. 전체 서비스 실행 (DB, App)
docker compose -f docker-compose.prod.yml up --build -d
```
