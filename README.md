# MRO Backend (Spring Boot)

인증·권한, 접수 확인, 요청 상태 관리, 배정 실행, 대시보드 API를 맡는 백엔드입니다.
에이전트(LLM1~3)는 별도 Python AI 서버가 실행하고, Spring과 AI 서버는 RabbitMQ로 주고받습니다.
계약은 팀 문서 "MRO Agent 팀 인터페이스 규약"을 따릅니다.

## 실행

**팀원 (Java 없이 전체 실행)**

```bash
docker compose up -d --build     # Postgres + RabbitMQ + 백엔드 + RAG 데이터 복원 (첫 빌드 1~2분)
docker compose logs -f backend   # 로그 확인
```

**백엔드 개발 (코드 수정하며 실행)**

```bash
docker compose up -d postgres rabbitmq
./gradlew bootRun                # local 프로파일: 빈 DB면 seed/*.csv 적재, 앱 안의 가짜 AI 서버가 큐를 소비
./scripts/restore-data.sh        # (다른 터미널에서, 처음 한 번) RAG·기술자료 복원. 약 20초
```

- Swagger: http://localhost:8080/swagger-ui.html
- RabbitMQ 관리 UI: http://localhost:15672 (DLQ 메시지 확인·재전송)
- 헬스: http://localhost:8080/actuator/health — `ragData`가 `UNKNOWN`이면 RAG 데이터 복원 전
- 시드 계정 비밀번호는 모두 `test1234` (`seed/users.csv` 참고)
- 신고자는 `POST /api/v1/auth/signup`(email, password 8자 이상, name, airline_code)으로 직접 가입하고 바로 로그인 토큰을 받습니다. 정비팀·어드민 계정은 시드로만 만듭니다. 가입 화면용 항공사 목록 `GET /api/v1/airlines`는 인증 없이 열려 있습니다
- 처음부터 다시 하려면 `docker compose down -v` (DB·큐 데이터 삭제)
- 시드 CSV(`seed/`): 수리이력은 경님 데이터(ATA 27 비행 조종 78건 추가), 계정은 임시 데이터로 주영님 데이터로 교체 예정
- 실제 AI 서버를 붙일 때는 `AI_MODE=rabbit docker compose up -d --build` (기본 `mock`이면 앱 안의 가짜 AI 서버가 실행 요청을 가져감)

데이터 전달본(RAG 약 124MB, 기술자료 약 1MB)은 git에 없습니다. 팀 공유 드라이브에서 받아 `data/rag/` 아래에 두세요. `scripts/restore-data.sh`가 둘 다 복원하며(compose에서는 `rag-restore` 서비스가 자동 실행), 전달본이 없으면 경고만 남기고 건너뜁니다. 스크립트는 체크섬·스키마 호환·Flyway 적용 여부를 확인한 뒤, RAG 테이블이 모두 비어 있을 때만 복원하고 `verify.sql`로 내용을 검증합니다. 이미 같은 데이터가 있으면 건너뛰고, 일부만 차 있으면 아무것도 지우지 않고 중단합니다.

### DB와 데이터 (백엔드가 모두 띄움)

DB 구조와 데이터 적재는 백엔드가 책임지고, MCP 서버들은 조회 전용 계정으로 읽기만 합니다.

| 단계 | 내용 |
| --- | --- |
| Flyway V1·V2 | 업무 테이블 (요청·배정·검토·기체·수리이력 등) |
| Flyway V3 | RAG 테이블 10개 (세환님 `agent` 저장소 제공 SQL 그대로) |
| Flyway V4 | 조회 전용 계정 권한: `mro_reader`(rag-mcp, RAG 테이블만), `mro_business_reader`(business-mcp, 기체·수리이력·부서만) |
| Flyway V5 | R04 기술자료 테이블 3개 (경님 전달본 SQL 그대로), `mro_reader` 읽기 권한 |
| SeedLoader | 업무 시드 `seed/*.csv` (local 프로파일) |
| RAG 데이터 | 세환님 전달본 `data/rag/2026-10-21-rag-v1/` → `scripts/rag-restore.sh`. SDR 71,741건, 근거 107,137건, 임베딩 31,678개 |
| 기술자료 (R04) | 경님 전달본 `data/rag/mro-tech-handoff-20261021-v1/` → `scripts/tech-restore.sh`. Airbus Safety First 61편, 조각·임베딩 496개. 문서별 담당 부서는 복원 마지막에 현재 부서 매핑으로 다시 계산 (`scripts/lib/tech-departments.sql`) |

조회 전용 계정 비밀번호는 Postgres 볼륨을 처음 만들 때 `docker/postgres/init`이 설정합니다 (`MRO_RAG_READER_PASSWORD`, `MRO_BUSINESS_READER_PASSWORD`, 기본값은 로컬 전용). 이미 볼륨이 있으면 `docker compose down -v` 후 다시 올려야 계정이 생깁니다.

### AI 서버 연결 (RabbitMQ)

```
mro.agent (direct) ── run.requested ──▶ mro.agent.run     Spring 발행 → AI 서버 소비 (TTL 5분)
                   └─ agent.event   ──▶ mro.agent.events  AI 서버 발행 → Spring 소비
mro.agent.dlx      ── run.dead      ──▶ mro.agent.run.dlq     Spring이 소비 → 사람 검토(SYSTEM_ERROR)
                   └─ event.dead    ──▶ mro.agent.events.dlq  보관 (관리 UI에서 확인·재전송)
```

- 큐·익스체인지는 Spring이 시작할 때 선언합니다. AI 서버는 선언하지 말고 소비·발행만 하세요.
- **MCP 위임**: 실행 요청마다 `run_id`를 새로 만들고 `rag_request_contexts`에 검색 범위(기종만 고정, 운항사 없음)를 등록한 뒤, business-mcp·rag-mcp용 토큰을 RabbitMQ 헤더 `x-mro-business-token`·`x-mro-rag-token`으로 붙입니다 (HS256, iss `mro-backend`, aud `mro-business`/`mro-rag`, 10분). AI 서버는 `MCP_DELEGATION_TRANSPORT=amqp_headers`로 받아 각 MCP에 `Authorization: Bearer`로 전달합니다 (`McpDelegationService`).
- business-mcp는 V6로 `repair_requests`의 확인용 열 8개만 읽습니다.
- 실행 요청을 AI 서버가 `reject(requeue=false)`하거나 5분 안에 아무도 가져가지 않으면 실행 DLQ로 가고, 해당 요청은 사람 검토(SYSTEM_ERROR)로 넘어갑니다.
- 이벤트 처리 중 형식 오류·없는 요청은 바로, 그 밖의 오류는 3회 재시도 후 이벤트 DLQ로 갑니다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `AI_MODE` | local·compose: `mock`, 그 외: `rabbit` | `mock`이면 앱 안의 `MockAiServer`가 실행 요청 큐를 소비해 키워드 기반 가짜 판단을 보냄. 실제 AI 서버와 동시에 켜지 마세요 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `mro` / `mro` | |
| `JWT_SECRET` | local 프로파일만 개발용 값 | 32바이트 이상. 그 밖의 프로파일에서는 없으면 시작하지 않음 |
| `CORS_ORIGINS` | `http://localhost:5173` | 쉼표로 여러 개 |
| `MCP_BUSINESS_JWT_SECRET` / `MCP_RAG_JWT_SECRET` | local 프로파일만 개발용 값 | MCP 위임 토큰 서명용. agent 저장소 `.env.mcp`의 `BUSINESS_JWT_SECRET`·`RAG_JWT_SECRET`과 같아야 함. 32바이트 이상, 서로 다르게 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/mro`, `mro`, `mro` | |

## 테스트

```bash
./gradlew test   # Testcontainers로 Postgres·RabbitMQ를 띄움 (Docker 필요)
# Postgres만 외부 DB로: TEST_DB_URL=jdbc:postgresql://localhost:5432/mro_test ./gradlew test
```

## 구조

`domain/<기능>/{controller, service, repository, entity, dto}` + `global/<공통>` 구조입니다.
컨트롤러는 서비스만 호출하고, 요청·응답 타입은 모두 `dto` 패키지의 별도 파일로 둡니다.

```
com.skala.mro
├── domain
│   ├── auth        로그인, 신고자 가입, 내 정보
│   ├── user        계정 엔티티 (신고자는 가입 API, 정비팀·어드민은 시드)
│   ├── master      기체·부서·항공사 마스터, 부서·항공사 목록 API
│   ├── request     수리 요청, 상태 머신, 접수 확인, 행 단위 권한
│   ├── agent       RabbitMQ 발행·소비(messaging), DLQ 처리, 이벤트 반영, 시간 초과 감시 (+ messaging, config)
│   ├── assignment  정비팀 대기함, 수락·거절
│   ├── review      사람 검토 큐, 어드민 수동 배정·종결
│   └── stats       어드민 요약 통계
└── global
    ├── config      OpenAPI
    ├── security    JWT 필터, SecurityConfig, AuthUser
    ├── error       ErrorCode, ApiException, 공통 에러 응답
    ├── common      PageResponse
    └── seed        local 프로파일 CSV 적재
```

핵심 클래스:
- `domain/request/service/RequestWorkflow`: 요청 상태를 바꾸는 유일한 경로 (전이 검증 + 상태 이력)
- `domain/request/entity/RequestStatus`: 허용되는 상태 전이 규칙
- `domain/request/service/RequestAccessPolicy`: 역할별 행 단위 접근 규칙
- `domain/agent/service/AgentEventService`: AI 이벤트 반영과 배정 실행
- `domain/agent/config/RabbitConfig`: 큐·DLQ 토폴로지와 재시도 정책

권한은 두 단계입니다. 역할이 맞지 않으면 403(`@PreAuthorize`), 역할은 맞지만 본인 것이 아니면 404(`RequestAccessPolicy`)입니다.
스키마 변경은 `src/main/resources/db/migration`에 새 Flyway 파일(`V2__...sql`)을 추가해서만 합니다.
