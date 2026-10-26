# MRO Backend (Spring Boot)

항공 정비 요청의 접수부터 배정까지를 관리하는 백엔드입니다. 인증·권한, 접수 검증, 요청 상태 관리, 배정 실행, 대시보드 API, 공유 DB와 데이터 적재를 맡습니다.
AI 판단(LLM1~3)은 별도 AI 서버가 하고, 둘은 RabbitMQ로 주고받습니다. 전체 구성·통합 실행·배포 흐름은 [integration](https://github.com/mro-agent-proj/integration)을 참고하세요.

```
[dashboard] ──HTTP──▶ [backend] ──run.requested (+MCP 토큰 2개)──▶ [ai: LLM1·2·3] ──MCP──▶ [business-mcp · rag-mcp]
                         │  ◀────────── agent.event ─────────────┘                          │
                         └──────────── Postgres (업무 + RAG + 기술자료) ◀── 조회 전용 계정 ────┘
```

요청 하나의 흐름:
1. 신고자가 접수하면 기체·기종·항공사를 검증하고 `QUEUED`로 저장합니다.
2. 커밋 후 검색 범위를 등록하고 MCP 토큰 2개를 붙여 실행 요청을 RabbitMQ에 발행합니다.
3. AI 서버가 단계 이벤트(`CLASSIFYING` → `INVESTIGATING` → `REVIEWING`)와 최종 판단을 보냅니다.
4. 근거·부서를 다시 검증해 배정(`ASSIGNED`)하거나 사람 검토(`HUMAN_REVIEW`)로 보냅니다.
5. 정비팀이 수락(`ACCEPTED`)하거나 거절하면 관리자가 수동 배정·종결합니다.

## 실행

### 백엔드만 (가짜 AI)

```bash
docker compose up -d --build     # Postgres + RabbitMQ + 백엔드 + RAG·기술자료 복원 (첫 빌드 1~2분)
docker compose logs -f backend
```

기본 `AI_MODE=mock`이면 앱 안의 `MockAiServer`가 증상 키워드로 가짜 판단을 보냅니다. 화면 흐름 확인용입니다.

### 실제 AI까지 전체

integration 레포의 `./scripts/up.sh`가 backend(`AI_MODE=rabbit`) → MCP 2개 → AI 서버를 순서대로 띄웁니다. 이 레포만 직접 띄울 때는 `AI_MODE=rabbit docker compose up -d --build`처럼 매번 지정해야 하며, 빼면 mock으로 돌아갑니다. mock과 실제 AI를 동시에 켜지 마세요(같은 큐를 나눠 가져갑니다).

### 코드 수정하며 개발

```bash
docker compose up -d postgres rabbitmq
./gradlew bootRun                # local 프로파일: 빈 DB면 data/seed/*.csv 적재
./scripts/restore-data.sh        # (처음 한 번) RAG·기술자료 복원, 약 20초
```

| 주소 | 용도 |
| --- | --- |
| http://localhost:8080/swagger-ui.html | Swagger |
| http://localhost:15672 | RabbitMQ 관리 UI (`mro` / `mro`, DLQ 확인·재전송) |
| http://localhost:8080/actuator/health | 헬스 (`ragData`가 `UNKNOWN`이면 RAG 복원 전) |
| http://localhost:8080/actuator/prometheus | Prometheus 지표(인증 없음): JVM·HTTP·DB 풀, 요청 상태 전이 `mro_request_transitions_total{to,reason,actor}` |

기존 DB·큐 볼륨은 지우지 않습니다.

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
| `maintenance_history_v2.csv` | 수리이력 1,600건 (SDR 기반 목업, 서비스 평가셋과 함께 버전 관리) |
| `technicians.csv` | 정비사 목업 38명 (8개 부서, 직급·근무조·출근·작업가능). 관리자 인원 현황판 전용이며 로그인 계정이 아님. 출근하지 않은 사람은 작업가능일 수 없음 |

`SeedLoader`는 users가 이미 있으면 업무 시드를 다시 넣지 않습니다. CSV를 교체하거나 컨테이너를 재빌드해도 기존 이력은 자동 갱신되지 않습니다. 평가 전 DB의 이력 버전을 확인하고, 별도 빈 평가 DB의 최초 적재 또는 Backend 담당자의 갱신 절차를 사용하세요. 실행 설정 `mro.seed.history-file`의 기본값은 `maintenance_history_v2.csv`입니다.

평가셋은 `data/eval/`에 있습니다([data/eval/README.md](data/eval/README.md)).

| 폴더 | 내용 |
| --- | --- |
| `service/` | 통합 평가 200건(dev 80/test 120). `scripts/scenario_eval.py --workers 1`로 실제 스택에 보내 채점. [실구동 결과](https://github.com/mro-agent-proj/deliverables/tree/main/evaluations/2026-10-23-isolated-200)는 deliverables에 보관 |
| `scenario/` | 접수 검증 V1~V6 |
| `sdr/` | FAA 공개 SDR 2019~2020 기반 단위 평가: 검색(R01·R02)·LLM1 분류·LLM2 부서·JASC 매핑. `scripts/build_sdr_eval.py`로 재생성 |

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
| `GET /staffing` | 관리자 | 인원 현황판(참고용). 전체·부서별 총원, 출근, 작업가능 인원과 명단. 배정·AI 판단에는 쓰이지 않음 |

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
- AI의 최종 판단은 백엔드가 다시 검증합니다(`AgentEventService`, `EvidenceValidator`). 없는 부서, 존재하지 않거나 Backend가 허용한 모델 범위 밖의 근거면 배정하지 않고 사람 검토(`VALIDATION_FAILED`)로 보냅니다. AI가 보낸 `RETRY_LIMIT`·`VALIDATION_FAILED` 사유는 그대로 저장합니다.

### MCP 위임 (토큰과 검색 범위)

실행 요청마다 `McpDelegationService`가 다음을 합니다.

1. `run_id`를 새로 만들고 `rag_request_contexts`에 검색 범위와 V11 모델 허용 목록을 등록합니다. 새 정책은 동일 기본 모델·세대를 기본 검색 범위로 쓰며 추가 조사에 허용 계열 범위를 제공합니다. 운항사는 시연 정책에 따라 비웁니다(KAL·AAR·JNA는 SDR 운항사가 아님).
2. business-mcp·rag-mcp용 토큰을 각각 서명해 RabbitMQ 헤더 `x-mro-business-token`, `x-mro-rag-token`으로 붙입니다.
   HS256, iss `mro-backend`, aud `mro-business` / `mro-rag`, 유효 10분. AI 서버는 이 토큰을 각 MCP에 `Authorization: Bearer`로 전달합니다.

MCP 서버는 조회 전용 계정으로 DB를 읽기만 합니다. business-mcp는 V9의 `agent_request_context`·`agent_run_context` 뷰를 사용합니다. V6의 원본 테이블 열 권한은 회수됐습니다.

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
│   ├── service/                      현재 통합 평가 200건 + manifest (결과 results/는 git 제외)
│   ├── scenario/                     접수 검증 6건
│   └── sdr/                          단위 평가: FAA SDR 기반 검색·분류·부서·매핑
├── rag-dump/                   제외  DB 복원용 덤프 (통합 레포 Release로 받음)
│   ├── sdr-jasc-amm_v2/              SDR·JASC·AMM 참조·근거 (57MB, V8 이후 6개 테이블)
│   ├── airbus-safety-first_v2/       기술자료: Airbus Safety First 61편, OpenAI 임베딩 (2MB)
│   └── sdr-embeddings_v1/            R02 하이브리드용 SDR 임베딩 (OpenAI 3-large, 1024차원, 317MB)
└── rag-csv/                    제외  덤프 내용을 사람이 보는 CSV로 뽑은 것 (확인·공유용)
    ├── sdr-by-year/                  SDR 원본 레코드, 연도별 CSV
    └── db-tables/                    RAG 테이블별 CSV (gzip)
```

| Flyway | 내용 |
| --- | --- |
| V1·V2 | 업무 테이블 (요청·배정·검토·기체·수리이력 등) |
| V3 | RAG 테이블 10개 (agent 레포 제공 SQL 그대로) |
| V4 | 조회 전용 계정 권한: `mro_reader`(rag-mcp, RAG 테이블), `mro_business_reader`(business-mcp, 기체·수리이력·부서) |
| V5 | R04 기술자료 테이블 3개 (전달본 SQL 그대로) |
| V6 | `mro_business_reader`에 `repair_requests` 확인용 열 8개 (V9에서 뷰로 대체) |
| V7 | R02 하이브리드용 SDR 임베딩 `rag.sdr_embeddings` (OpenAI text-embedding-3-large, 1024차원) |
| V8 | AMM 의미 검색 테이블 3개(`amm_chunks`, `amm_chunk_references`, `rag.amm_embeddings`) 제거, 기술자료 임베딩을 OpenAI 1024차원으로 교체. AMM은 판단 근거가 아니라 배정 후 참고 정보 |
| V9 | business-mcp용 AI 뷰 `agent_request_context`(접수 내용·상태·버전), `agent_run_context`(실행별 검색 범위). `mro_business_reader`는 이 뷰만 읽고, V6의 `repair_requests` 열 권한은 회수 |
| V10 | 인원 현황판용 `technicians` 표 (새 표 1개, 기존 표 변경 없음). 화면 전용이라 MCP 계정 둘 다 권한 없음 (`DatabaseSchemaTest`로 확인) |
| V11 | 실행별 모델 허용 목록·검색 범위 정책. 상세 계약은 `docs/MODEL_SCOPE.md` |

| 적재 | 내용 |
| --- | --- |
| SeedLoader | `data/seed/*.csv` (local 프로파일, users 테이블이 비어 있을 때만). `technicians.csv`는 technicians 테이블이 비어 있으면 따로 적재해 이미 쓰던 DB에도 들어감 |
| RAG 데이터 | 전달본 `data/rag-dump/sdr-jasc-amm_v2/` → `scripts/rag-restore.sh`. SDR 71,741건, AMM 참조 35,396건, 근거 107,137건 (v1 전달본은 V8 이후 복원 불가) |
| 기술자료 (R04) | 전달본 `data/rag-dump/airbus-safety-first_v2/` → `scripts/tech-restore.sh`. Airbus Safety First 61편, 조각 496개, OpenAI 임베딩 496개 |
| SDR 임베딩 (R02 하이브리드) | Flyway V7 `rag.sdr_embeddings` + 전달본 `data/rag-dump/sdr-embeddings_v1/` → `scripts/sdr-embed-restore.sh`. SDR 71,741건, OpenAI text-embedding-3-large 1024차원. 없으면 R02는 단어 검색만 함 |

데이터 전달본(RAG 약 124MB, 기술자료 약 1MB)은 git에 없습니다. [통합 레포](https://github.com/mro-agent-proj/integration)의 `scripts/fetch-data.sh`로 받으면 `data/rag-dump/`(복원용 덤프)와 `data/rag-csv/`(확인용 CSV)에 풀립니다. `scripts/restore-data.sh`가 둘 다 복원하며(compose에서는 `rag-restore` 서비스가 자동 실행), 전달본이 없으면 경고만 남기고 건너뜁니다.

- 복원 전 체크섬·스키마 호환·Flyway 적용 여부를 확인하고, 테이블이 모두 비어 있을 때만 복원한 뒤 `verify.sql`로 검증합니다.
- 이미 같은 데이터가 있으면 건너뛰고, 일부만 차 있으면 아무것도 지우지 않고 중단합니다.
- 기술자료 복원 마지막에 문서별 담당 부서를 현재 부서 매핑으로 다시 계산합니다(`scripts/lib/tech-departments.sql`). 부서가 바뀌면 `scripts/tech-restore.sh`만 다시 실행하면 됩니다.

조회 전용 계정 비밀번호는 Postgres 볼륨을 처음 만들 때 `docker/postgres/init`이 설정합니다(`MRO_RAG_READER_PASSWORD`, `MRO_BUSINESS_READER_PASSWORD`, 기본값은 로컬 전용). 기존 볼륨에는 다시 실행되지 않습니다.

## 테스트

```bash
python3 -m unittest discover -s scripts/tests -v  # 오프라인 채점·평가셋 버전 검사
./gradlew test   # Testcontainers로 Postgres·RabbitMQ를 띄움 (Docker 필요)
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
│   ├── staffing    관리자 인원 현황판 (참고용)
│   └── stats       관리자 요약 통계
└── global
    ├── config      OpenAPI
    ├── security    JWT 필터, SecurityConfig, AuthUser
    ├── error       ErrorCode, ApiException, 공통 에러 응답
    ├── common      PageResponse
    ├── health      RAG 데이터 헬스 지표
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

- 근거의 존재·허용 모델 범위·버전 등은 Backend가 검증합니다. 증상과 근거의 의미적 관련성은 AI 서버 쪽 검토에 맡깁니다.
- 검색 범위는 `docs/MODEL_SCOPE.md`의 시연 정책(Backend 모델 허용 목록)입니다. 항공사 × 기종으로 고정하려면 우리 항공사와 SDR 운항사 매핑을 정한 뒤 `operator_code`를 채우면 됩니다.
- 로그인·가입 API에 시도 횟수 제한이 없습니다(Redis 도입 시 우선 과제).

## 문서

- [모델 검색 범위 계약](docs/MODEL_SCOPE.md): 검색 범위 확장과 서비스 간 적용 순서
- [데이터 폴더 안내](data/README.md), [평가셋 안내](data/eval/README.md)
