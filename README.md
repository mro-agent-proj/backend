# MRO Backend (Spring Boot)

항공 정비 요청의 접수부터 배정까지 업무를 관리하는 백엔드입니다.
인증·권한, 접수 검증, 요청 상태 관리, 배정 실행, 대시보드 API, 그리고 공유 DB와 데이터 적재를 맡습니다.
AI 판단(LLM1~3)은 별도 AI 서버가 하고, 백엔드와 AI 서버는 RabbitMQ로 주고받습니다.
계약은 팀 문서 "MRO Agent 팀 인터페이스 규약"을 따릅니다.

## 전체 구성

```
[dashboard] ──HTTP──▶ [backend] ──run.requested (+MCP 토큰 2개)──▶ [ai: LLM1·2·3] ──MCP──▶ [business-mcp · rag-mcp]
                         │  ◀────────── agent.event ─────────────┘                          │
                         └──────────── Postgres (업무 + RAG + 기술자료) ◀── 조회 전용 계정 ────┘
```

| 레포 | 역할 |
| --- | --- |
| [dashboard](https://github.com/mro-agent-proj/dashboard) | 신고자·정비팀·관리자 화면 |
| [backend](https://github.com/mro-agent-proj/backend) | 이 레포. 업무 상태·배정·DB의 주인 |
| [LLM](https://github.com/mro-agent-proj/LLM) | AI 서버 (FastAPI + LangGraph) |
| [agent](https://github.com/mro-agent-proj/agent) | MCP 서버 2개 (읽기 전용) |

요청 하나의 흐름:
1. 신고자가 접수하면 백엔드가 기체·기종·항공사를 검증하고 `QUEUED`로 저장합니다.
2. 커밋 후 실행 요청을 RabbitMQ에 발행합니다. 이때 검색 범위를 등록하고 MCP 토큰 2개를 헤더에 붙입니다.
3. AI 서버가 단계 이벤트(`CLASSIFYING` → `INVESTIGATING` → `REVIEWING`)와 최종 판단을 보냅니다.
4. 백엔드가 근거·부서를 검증해 배정(`ASSIGNED`)하거나 사람 검토(`HUMAN_REVIEW`)로 보냅니다.
5. 정비팀이 수락(`ACCEPTED`)하거나 거절하면 관리자가 수동 배정·종결합니다.

## 실행

### 백엔드만 (가짜 AI)

```bash
docker compose up -d --build     # Postgres + RabbitMQ + 백엔드 + RAG·기술자료 복원 (첫 빌드 1~2분)
docker compose logs -f backend
```

기본 `AI_MODE=mock`이면 앱 안의 `MockAiServer`가 증상 키워드로 가짜 판단을 보냅니다. 화면 흐름 확인용입니다.

### 실제 AI까지 전체

```bash
# 1) backend (이 레포)
AI_MODE=rabbit docker compose up -d --build
# 2) agent 레포: .env.mcp를 채운 뒤 MCP 두 개 (같은 Docker 네트워크 backend_default에 붙음)
docker compose --env-file .env.mcp -f compose.mcp.yaml up -d --build
# 3) LLM 레포: .env에 LLM_API_KEY 등을 채운 뒤 AI 서버
docker compose up -d --build
# 4) dashboard 레포
pnpm dev
```

`AI_MODE=rabbit`은 실행할 때마다 지정해야 합니다. 그냥 `docker compose up`을 하면 mock으로 돌아갑니다. mock과 실제 AI를 동시에 켜지 마세요(같은 큐를 나눠 가져갑니다).

### 코드 수정하며 개발

```bash
docker compose up -d postgres rabbitmq
./gradlew bootRun                # local 프로파일: 빈 DB면 data/seed/*.csv 적재
./scripts/restore-data.sh        # (처음 한 번) RAG·기술자료 복원, 약 20초
```

- Swagger: http://localhost:8080/swagger-ui.html
- RabbitMQ 관리 UI: http://localhost:15672 (`mro` / `mro`, DLQ 확인·재전송)
- 헬스: http://localhost:8080/actuator/health (`ragData`가 `UNKNOWN`이면 RAG 복원 전)
- 처음부터 다시: `docker compose down -v` (DB·큐 삭제. 시드와 RAG는 다시 올릴 때 자동 적재)

## 계정과 시드 데이터

시드 계정 비밀번호는 모두 `test1234`입니다(`data/seed/users.csv`).

| 역할 | 계정 |
| --- | --- |
| 관리자 | `admin@mro.test` |
| 정비팀 (부서별 1개) | `avi@` `cab@` `ele@` `eng@` `fcs@` `hyd@` `str@` `sys@mro.test` |
| 신고자 | `reporter1@kal.test` 등 항공사별 2개 (KAL·AAR·JNA) |

신고자는 `POST /api/v1/auth/signup`으로 직접 가입할 수도 있습니다. 정비팀·관리자 계정은 시드로만 만듭니다.

| `data/seed/` | 내용 |
| --- | --- |
| `departments.csv` | 정비 부서 8개와 담당 ATA. 항전 AVI, 객실 CAB, 전기 ELE, 엔진 ENG, 비행 조종 FCS(ATA 27), 유압·착륙장치 HYD, 구조 STR, 기계 계통 SYS |
| `airlines.csv` / `aircraft.csv` | 항공사 3곳, A320 계열 기체 20대 (HL7720은 운용 중지) |
| `maintenance_history.csv` | 수리이력 1,577건 (FAA SDR 실제 결함을 한국어로 다시 쓴 목업, 경님 데이터 + ATA 27 78건) |

평가셋은 `data/eval/`에 두 가지가 있습니다. 자세한 내용은 [data/eval/README.md](data/eval/README.md).
- `scenario/` 통합 평가: AI 경로 S01~S23(기대 경로·부서·근거 이력)과 접수 검증 V1~V6. `scripts/scenario_eval.py`로 실제 스택에 보내 채점합니다.
- `sdr/` 단위 평가: FAA 공개 SDR 2019~2020(검색 대상과 기간이 안 겹침)으로 만든 검색(R01·R02)·LLM1 분류·LLM2 부서 결정·JASC 매핑 평가셋. `scripts/build_sdr_eval.py`로 다시 만들 수 있습니다.

계정 시드는 임시 데이터이며 주영님 데이터로 교체 예정입니다.

## 주요 API

모든 경로는 `/api/v1` 아래이고, 응답 필드는 snake_case, 에러는 `{ "error": { "code", "message", "details" } }` 형식입니다.

| API | 권한 | 설명 |
| --- | --- | --- |
| `POST /auth/login`, `POST /auth/signup`, `GET /me` | 공개 / 로그인 | 로그인, 신고자 가입(바로 로그인), 내 정보 |
| `GET /airlines`, `GET /departments` | 공개 / 로그인 | 가입 화면용 항공사, 부서 목록 |
| `POST /requests` | 신고자 | 접수. 누락·불일치면 422 `NEEDS_SUPPLEMENT`(등록 정보 `expected` 포함) |
| `GET /requests`, `GET /requests/{id}` | 역할별 | 역할에 따라 볼 수 있는 범위만 조회 |
| `GET /requests/{id}/trace` | 정비팀·관리자 | AI 단계별 판단(assessments)과 상태 이력 |
| `GET /assignments`, `POST /assignments/{id}/accept`·`reject` | 정비팀 | 우리 부서 배정 대기함, 수락·거절 |
| `GET /reviews`, `POST /reviews/{id}/resolve` | 관리자 | 사람 검토 큐, 수동 배정(ASSIGN)·종결(CLOSE) |
| `GET /stats/summary` | 관리자 | 상태별·부서별 건수, 자동 배정률, 첫 배정까지 평균 시간 |

권한은 두 단계입니다. 역할이 다르면 403(`@PreAuthorize`), 역할은 맞지만 본인 것이 아니면 404(`RequestAccessPolicy`)입니다.

## AI 서버 연결 (RabbitMQ)

```
mro.agent (direct) ── run.requested ──▶ mro.agent.run     Spring 발행 → AI 서버 소비 (TTL 5분)
                   └─ agent.event   ──▶ mro.agent.events  AI 서버 발행 → Spring 소비
mro.agent.dlx      ── run.dead      ──▶ mro.agent.run.dlq     Spring이 소비 → 사람 검토(SYSTEM_ERROR)
                   └─ event.dead    ──▶ mro.agent.events.dlq  보관 (관리 UI에서 확인·재전송)
```

- 큐·익스체인지는 Spring이 시작할 때 선언합니다. AI 서버는 소비·발행만 합니다.
- AI 서버가 실행 요청을 `reject(requeue=false)`하거나 5분 안에 아무도 가져가지 않으면 실행 DLQ로 가고, 요청은 사람 검토(`SYSTEM_ERROR`)로 넘어갑니다.
- 이벤트 처리 중 형식 오류·없는 요청은 바로, 그 밖의 오류는 3회 재시도 후 이벤트 DLQ로 갑니다. 같은 `event_id`는 한 번만 반영합니다.
- AI의 최종 판단은 백엔드가 다시 검증합니다(`AgentEventService`, `EvidenceValidator`). 없는 부서, 존재하지 않거나 기종이 다른 근거면 배정하지 않고 사람 검토(`VALIDATION_FAILED`)로 보냅니다. AI가 보낸 `RETRY_LIMIT`·`VALIDATION_FAILED` 사유는 그대로 저장합니다.

### MCP 위임 (토큰과 검색 범위)

실행 요청마다 `McpDelegationService`가 다음을 합니다.

1. `run_id`를 새로 만들고 `rag_request_contexts`에 검색 범위를 등록합니다. 시연 정책에 따라 **기종만 고정하고 운항사는 비웁니다**(KAL·AAR·JNA는 SDR 운항사가 아니기 때문).
2. business-mcp·rag-mcp용 토큰을 각각 서명해 RabbitMQ 헤더 `x-mro-business-token`, `x-mro-rag-token`으로 붙입니다.
   HS256, iss `mro-backend`, aud `mro-business` / `mro-rag`, 유효 10분. AI 서버는 이 토큰을 각 MCP에 `Authorization: Bearer`로 전달합니다.

MCP 서버는 조회 전용 계정으로 DB를 읽기만 합니다. business-mcp는 V6로 `repair_requests`의 확인용 열 8개만 읽습니다.

### 환경변수

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `AI_MODE` | local·compose: `mock`, 그 외: `rabbit` | `mock`이면 앱 안의 가짜 AI가 실행 요청을 소비 |
| `JWT_SECRET` | local 프로파일만 개발용 값 | 로그인 토큰 서명. 32바이트 이상, 그 밖의 프로파일에서는 없으면 시작 안 함 |
| `MCP_BUSINESS_JWT_SECRET` / `MCP_RAG_JWT_SECRET` | local 프로파일만 개발용 값 | MCP 토큰 서명. agent 레포 `.env.mcp`의 `BUSINESS_JWT_SECRET`·`RAG_JWT_SECRET`과 같아야 함. 32바이트 이상, 서로 다르게 |
| `CORS_ORIGINS` | `http://localhost:5173` | 쉼표로 여러 개 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/mro`, `mro`, `mro` | |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `localhost` / `5672` / `mro` / `mro` | |

## DB와 데이터

DB 구조와 데이터 적재는 백엔드가 책임집니다. 스키마 변경은 `src/main/resources/db/migration`에 새 Flyway 파일을 추가해서만 합니다.

```
data/
├── seed/                       git   업무 시드 CSV (항공사·기체·부서·계정·수리이력)
├── eval/                       git   평가셋
│   ├── scenario/                     통합 평가: 시나리오 23건 + 접수 검증 6건 (결과 results/는 git 제외)
│   └── sdr/                          단위 평가: FAA SDR 기반 검색·분류·부서·매핑
├── rag-dump/                   제외  DB 복원용 덤프 (통합 레포 Release로 받음)
│   ├── sdr-jasc-amm_v1/              SDR·JASC·AMM·근거·임베딩 (124MB)
│   └── airbus-safety-first_v1/       기술자료: Airbus Safety First 61편 (1MB)
└── rag-csv/                    제외  덤프 내용을 사람이 보는 CSV로 뽑은 것 (확인·공유용)
    ├── sdr-by-year/                  SDR 원본 레코드, 연도별 CSV
    └── db-tables/                    RAG 테이블별 CSV (gzip)
```

| 단계 | 내용 |
| --- | --- |
| Flyway V1·V2 | 업무 테이블 (요청·배정·검토·기체·수리이력 등) |
| Flyway V3 | RAG 테이블 10개 (agent 레포 제공 SQL 그대로) |
| Flyway V4 | 조회 전용 계정 권한: `mro_reader`(rag-mcp, RAG 테이블), `mro_business_reader`(business-mcp, 기체·수리이력·부서) |
| Flyway V5 | R04 기술자료 테이블 3개 (경님 전달본 SQL 그대로) |
| Flyway V6 | `mro_business_reader`에 `repair_requests` 확인용 열 8개 |
| SeedLoader | `data/seed/*.csv` (local 프로파일, users 테이블이 비어 있을 때만) |
| RAG 데이터 | 전달본 `data/rag-dump/sdr-jasc-amm_v1/` → `scripts/rag-restore.sh`. SDR 71,741건, 근거 107,137건, 임베딩 31,678개 |
| 기술자료 (R04) | 전달본 `data/rag-dump/airbus-safety-first_v1/` → `scripts/tech-restore.sh`. Airbus Safety First 61편, 조각·임베딩 496개 |

데이터 전달본(RAG 약 124MB, 기술자료 약 1MB)은 git에 없습니다. [통합 레포](https://github.com/mro-agent-proj/mro-agent-integration)의 `scripts/fetch-data.sh`로 받으면 `data/rag-dump/`(복원용 덤프)와 `data/rag-csv/`(확인용 CSV)에 풀립니다. `scripts/restore-data.sh`가 둘 다 복원하며(compose에서는 `rag-restore` 서비스가 자동 실행), 전달본이 없으면 경고만 남기고 건너뜁니다.

- 복원 전 체크섬·스키마 호환·Flyway 적용 여부를 확인하고, 테이블이 모두 비어 있을 때만 복원한 뒤 `verify.sql`로 검증합니다.
- 이미 같은 데이터가 있으면 건너뛰고, 일부만 차 있으면 아무것도 지우지 않고 중단합니다.
- 기술자료 복원 마지막에 문서별 담당 부서를 현재 부서 매핑으로 다시 계산합니다(`scripts/lib/tech-departments.sql`). 부서가 바뀌면 `scripts/tech-restore.sh`만 다시 실행하면 됩니다.

조회 전용 계정 비밀번호는 Postgres 볼륨을 처음 만들 때 `docker/postgres/init`이 설정합니다(`MRO_RAG_READER_PASSWORD`, `MRO_BUSINESS_READER_PASSWORD`, 기본값은 로컬 전용). 이미 볼륨이 있으면 `docker compose down -v` 후 다시 올려야 계정이 생깁니다.

## 테스트

```bash
./gradlew test   # Testcontainers로 Postgres·RabbitMQ를 띄움 (Docker 필요). 35개
# Postgres만 외부 DB로: TEST_DB_URL=jdbc:postgresql://localhost:5432/mro_test ./gradlew test
```

테스트는 AI 서버 대신 이벤트 큐에 직접 발행합니다. 가입·권한, 요청 흐름(재조사·거절·수동 배정·DLQ), MCP 토큰·검색 범위 등록, 근거 검증, 조회 전용 계정 권한을 확인합니다.

## 코드 구조

`domain/<기능>/{controller, service, repository, entity, dto}` + `global/<공통>` 구조입니다.
컨트롤러는 서비스만 호출하고, 요청·응답 타입은 모두 `dto` 패키지의 별도 파일로 둡니다.

```
com.skala.mro
├── domain
│   ├── auth        로그인, 신고자 가입, 내 정보
│   ├── user        계정 엔티티 (신고자는 가입 API, 정비팀·관리자는 시드)
│   ├── master      기체·부서·항공사 마스터, 부서·항공사 목록 API
│   ├── request     수리 요청, 상태 머신, 접수 확인, 행 단위 권한
│   ├── agent       RabbitMQ 발행·소비, MCP 위임, DLQ 처리, 이벤트 반영, 시간 초과 감시, 가짜 AI
│   ├── assignment  정비팀 대기함, 수락·거절
│   ├── review      사람 검토 큐, 관리자 수동 배정·종결
│   └── stats       관리자 요약 통계
└── global
    ├── config      OpenAPI
    ├── security    JWT 필터, SecurityConfig, AuthUser
    ├── error       ErrorCode, ApiException, 공통 에러 응답
    ├── common      PageResponse
    └── seed        local 프로파일 CSV 적재
```

핵심 클래스:
- `request/service/RequestWorkflow`: 요청 상태를 바꾸는 유일한 경로 (전이 검증 + 상태 이력)
- `request/entity/RequestStatus`: 허용되는 상태 전이 규칙
- `request/service/RequestAccessPolicy`: 역할별 행 단위 접근 규칙
- `agent/service/AgentEventService`: AI 이벤트 반영과 배정 실행
- `agent/service/McpDelegationService`: 실행별 검색 범위 등록과 MCP 토큰 서명
- `agent/config/RabbitConfig`: 큐·DLQ 토폴로지와 재시도 정책

## 알려진 한계

- 근거 검증은 이력이 실제로 있는지, 같은 기종인지만 봅니다. 증상과 관련 있는지는 AI 서버 쪽 검증에 맡깁니다.
- 검색 범위는 시연 정책(기종만 고정)입니다. 항공사 × 기종으로 고정하려면 우리 항공사와 SDR 운항사 매핑을 정한 뒤 `operator_code`를 채우면 됩니다.
- 로그인·가입 API에 시도 횟수 제한이 없습니다(Redis 도입 시 우선 과제).
