
---

## Base URL

```
http://15.164.222.4:8000
```

- 인증 불필요 (토큰 없이 바로 호출 가능)
- 현재 데이터: **40개 문서, 4,448개 청크** (학칙 + 각종 규정 + 요람 + 공지사항)

---

## 빠른 시작

```python
import requests

BASE_URL = "http://15.164.222.4:8000"

# 1) 서버 상태 확인
requests.get(f"{BASE_URL}/health").json()
# → {"status": "ok"}

# 2) 현재 데이터 통계 확인
stats = requests.get(f"{BASE_URL}/api/chunks/stats").json()
print(f"문서 {stats['total_docs']}개, 청크 {stats['total_chunks']}개")
# → 문서 40개, 청크 4448개

# 3) 전체 청크 가져오기 (벡터DB 초기 구축용)
data = requests.get(f"{BASE_URL}/api/chunks/all").json()
chunks = data["chunks"]  # 4448개 청크 리스트

for chunk in chunks:
    text = chunk["content"]        # 임베딩할 텍스트
    chunk_id = chunk["id"]         # UUID (벡터DB ID로 사용)
    doc_name = chunk["doc_name"]   # 문서명
    section = chunk["section_path"] # 섹션
    page = chunk["page"]           # 페이지 번호
    category = chunk["category"]   # 문서 분류 (핵심학사/학생지원/대학생활/공지사항)
    created = chunk["created_at"]  # 생성 시각
    # → 여기서 임베딩 생성 후 벡터DB에 저장
```

---

## API 목록

| API | 설명 | 용도 |
|-----|------|------|
| `GET /api/chunks/all` | 전체 청크 일괄 조회 | 벡터DB 초기 구축 |
| `GET /api/chunks?page=0&size=100` | 페이징 조회 | 점진적 동기화 |
| `GET /api/chunks?docName=학칙.pdf` | 특정 문서 청크만 | 문서별 처리 |
| `GET /api/chunks/{id}` | 단건 조회 | 개별 청크 확인 |
| `GET /api/chunks/by-doc?docName=학칙.pdf` | 메타데이터만 (content 제외) | 목록 확인 (가벼움) |
| `GET /api/chunks/stats` | 통계 요약 | 변경 감지, 모니터링 |
| `GET /health` | 서버 상태 | 헬스체크 |

---

### 1. 전체 청크 일괄 조회

```
GET /api/chunks/all
```

벡터DB를 처음 구축할 때 사용하세요. 전체 청크를 한 번에 반환합니다.

```bash
curl http://15.164.222.4:8000/api/chunks/all
```

**응답:**
```json
{
  "chunks": [
    {
      "id": "cc635d18-69c2-43ce-a514-25cce1ef715b",
      "content": "국민대학교 학칙\n제정 1948년 8월 13일\n153차 개정 2025년 9월 29일\n제1장 총칙\n제1조(목적) 본 대학교는...",
      "doc_name": "학칙.pdf",
      "section_path": "제1장: 총칙",
      "page": 1,
      "has_table": false,
      "source_url": "",
      "category": "핵심학사",
      "created_at": "2026-03-18T14:30:00"
    },
    ...
  ],
  "total": 4448
}
```

---

### 2. 페이징 조회

```
GET /api/chunks?page=0&size=100
GET /api/chunks?docName=학칙.pdf&page=0&size=50
```

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `docName` | string | (전체) | 특정 문서만 필터 |
| `page` | int | 0 | 페이지 번호 (0부터) |
| `size` | int | 100 | 페이지당 청크 수 |

```bash
curl "http://15.164.222.4:8000/api/chunks?page=0&size=2"
```

**응답:**
```json
{
  "chunks": [...],
  "total": 4448,
  "page": 0,
  "size": 2,
  "total_pages": 2224
}
```

---

### 3. 단건 조회

```
GET /api/chunks/{id}
```

```bash
curl http://15.164.222.4:8000/api/chunks/cc635d18-69c2-43ce-a514-25cce1ef715b
```

---

### 4. 메타데이터만 조회 (content 제외)

```
GET /api/chunks/by-doc?docName=학칙.pdf
```

content 없이 메타데이터만 반환하므로 가볍습니다. 청크 목록만 확인할 때 사용하세요.

---

### 5. 통계 요약

```
GET /api/chunks/stats
```

```bash
curl http://15.164.222.4:8000/api/chunks/stats
```

**응답 (실제 데이터):**
```json
{
  "total_docs": 40,
  "total_chunks": 4448,
  "documents": [
    { "doc_name": "2025국민대학교요람.pdf", "chunk_count": 3605 },
    { "doc_name": "학칙.pdf", "chunk_count": 161 },
    { "doc_name": "학사규정.pdf", "chunk_count": 144 },
    { "doc_name": "현장실습 운영에 관한 내규.pdf", "chunk_count": 37 },
    { "doc_name": "국민대학교 학생연구자 지원규정.pdf", "chunk_count": 35 },
    { "doc_name": "원격수업 운영 규정.pdf", "chunk_count": 33 },
    { "doc_name": "장학규정.pdf", "chunk_count": 32 },
    ...
  ]
}
```

---

## 청크 데이터 필드 설명

| 필드 | 타입 | 설명 | 예시 |
|------|------|------|------|
| `id` | string (UUID) | 청크 고유 ID (벡터DB ID로 사용) | `cc635d18-69c2-43ce-...` |
| `content` | string | 청크 텍스트 (**임베딩 대상**) | `"제1조(목적) 본 대학교는..."` |
| `doc_name` | string | 원본 문서 파일명 | `"학칙.pdf"`, `"[공지] 제목.pdf"` |
| `section_path` | string | 섹션 경로 | `"제1장: 총칙"`, `"본문"` |
| `page` | int | 원본 문서 페이지 번호 | `1`, `15` |
| `has_table` | boolean | 표 포함 여부 | `false` |
| `source_url` | string | 원본 URL (공지사항인 경우) | `"https://cs.kookmin.ac.kr/..."` |
| `category` | string | 문서 분류 | `"핵심학사"`, `"학생지원"`, `"대학생활"`, `"공지사항"` |
| `created_at` | string (ISO) | 청크 생성 시각 | `"2026-03-18T14:30:00"` |

- 청크 크기: 550자 단위, 100자 오버랩
- `doc_name`이 `[공지]`로 시작하면 크롤링된 공지사항 데이터
- `category` 값: `핵심학사` / `학생지원` / `대학생활` / `공지사항`
- 주요 문서: 요람(3,605), 학칙(161), 학사규정(144), 장학규정(32) 등 총 40개 문서

---

## 데이터 동기화

백엔드에서 새 PDF 업로드 또는 공지 크롤링 시 청크가 자동 추가됩니다.

### 방법 1: stats로 변경 감지 후 동기화

```python
import requests

BASE_URL = "http://15.164.222.4:8000"

# 마지막으로 알고 있는 청크 수 (파일이나 DB에 저장해두기)
last_known_count = 4448

stats = requests.get(f"{BASE_URL}/api/chunks/stats").json()
if stats["total_chunks"] > last_known_count:
    print(f"새 데이터 감지! {last_known_count} → {stats['total_chunks']}")
    # 전체 재동기화
    chunks = requests.get(f"{BASE_URL}/api/chunks/all").json()["chunks"]
    for chunk in chunks:
        # 벡터DB에 upsert (id 기반이라 중복 없음)
        vector_db.upsert(id=chunk["id"], text=chunk["content"], metadata={
            "doc_name": chunk["doc_name"],
            "section_path": chunk["section_path"],
            "page": chunk["page"],
            "has_table": chunk["has_table"],
            "source_url": chunk["source_url"],
            "category": chunk["category"],
            "created_at": chunk["created_at"]
        })
    last_known_count = stats["total_chunks"]
```

### 방법 2: 주기적 전체 동기화

```python
# 단순하게 전체 가져와서 upsert (id 기반이라 기존 데이터는 덮어쓰기)
chunks = requests.get(f"{BASE_URL}/api/chunks/all").json()["chunks"]
for chunk in chunks:
    vector_db.upsert(id=chunk["id"], text=chunk["content"], metadata=chunk)
```

---

## 참고

- EC2 IP: `15.164.222.4` / 포트: `8000`
