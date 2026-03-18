#!/bin/bash
# KDD Backend 배포 스크립트
# EC2에서 실행: bash deploy.sh

set -e

echo "=== KDD Backend 배포 시작 ==="

# 1. 프로젝트 디렉토리 설정
APP_DIR=~/kdd-backend
mkdir -p $APP_DIR
cd $APP_DIR

# 2. 기존 backend 폴더가 없으면 안내
if [ ! -f "build.gradle" ]; then
    echo ""
    echo "❌ backend 파일이 아직 없습니다."
    echo ""
    echo "아래 방법 중 하나로 파일을 올려주세요:"
    echo ""
    echo "방법 1) GitHub에서 clone:"
    echo "  git clone -b SangJin https://github.com/KDD-Kookmin-Digital-Doc/kdd-api.git $APP_DIR"
    echo ""
    echo "방법 2) 로컬에서 scp로 전송:"
    echo "  scp -r backend/* ubuntu@3.35.208.60:~/kdd-backend/"
    echo ""
    echo "파일 전송 후 이 스크립트를 다시 실행하세요."
    exit 1
fi

# 3. .env 파일 확인
if [ ! -f ".env" ]; then
    echo ""
    echo "⚠️  .env 파일이 없습니다. .env.example에서 복사합니다."
    cp .env.example .env
    echo ""
    echo ">>> .env 파일을 열어서 실제 API 키를 입력해주세요:"
    echo "    nano $APP_DIR/.env"
    echo ""
    echo "필수 입력 항목:"
    echo "  GEMINI_API_KEY=실제_키"
    echo "  COHERE_API_KEY=실제_키"
    echo "  GOOGLE_CLIENT_ID=실제_클라이언트ID"
    echo "  JWT_SECRET=32자이상_랜덤문자열"
    echo ""
    echo ".env 설정 완료 후 이 스크립트를 다시 실행하세요."
    exit 1
fi

# 4. Docker 설치 확인
if ! command -v docker &> /dev/null; then
    echo "Docker 설치 중..."
    sudo apt-get update
    sudo apt-get install -y docker.io docker-compose-plugin
    sudo usermod -aG docker $USER
    echo "Docker 설치 완료. 로그아웃 후 재접속한 뒤 다시 실행하세요."
    exit 1
fi

# 5. 기존 컨테이너 중지
echo "기존 컨테이너 정리..."
docker compose down 2>/dev/null || docker-compose down 2>/dev/null || true

# 6. Docker 빌드 + 실행
echo "Docker 빌드 + 실행 중..."
if command -v docker compose &> /dev/null; then
    docker compose up --build -d
else
    docker-compose up --build -d
fi

# 7. 헬스체크 (최대 60초 대기)
echo "서버 시작 대기 중..."
for i in $(seq 1 30); do
    sleep 2
    if curl -s http://localhost:8000/health | grep -q "ok"; then
        echo ""
        echo "=== ✅ 배포 완료 ==="
        echo ""
        echo "서버 URL: http://$(curl -s ifconfig.me 2>/dev/null || echo '15.164.222.4'):8000"
        echo ""
        echo "테스트:"
        echo "  curl http://localhost:8000/health"
        echo "  curl http://localhost:8000/api/chunks/stats"
        echo ""
        echo "AI팀 연동 문서: API_FOR_AI_TEAM.md"
        echo ""
        exit 0
    fi
    echo -n "."
done

echo ""
echo "⚠️  서버가 아직 시작되지 않았습니다. 로그를 확인하세요:"
echo "  docker compose logs -f app"
