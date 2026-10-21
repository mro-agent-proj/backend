# MRO Backend (Spring Boot)

인증·권한, 접수 확인, 요청 상태 관리, 배정 실행, 대시보드 API를 맡는 백엔드입니다.
에이전트(LLM1~3)는 별도 Python AI 서버가 실행하고, Spring과 AI 서버는 RabbitMQ로 주고받습니다.
계약은 팀 문서 "MRO Agent 팀 인터페이스 규약"을 따릅니다.

## 실행

```bash
docker compose up -d          # Postgres 16 + pgvector (5432), RabbitMQ 4.1 (5672, 관리 UI 15672) — 계정 mro/mro
./gradlew bootRun             # local 프로파일: 빈 DB면 seed/*.csv 적재, 앱 안의 가짜 AI 서버가 큐를 소비
./scripts/rag-restore.sh      # (다른 터미널에서, 처음 한 번) RAG 데이터 복원. 약 20초
```

RAG 데이터 전달본(약 124MB)은 git에 없습니다. 팀 공유 드라이브에서 받아 `data/rag/2026-10-21-rag-v1/`에 두세요. 스크립트는 체크섬·스키마 호환·Flyway 적용 여부를 확인한 뒤, RAG 테이블이 모두 비어 있을 때만 복원하고 `verify.sql`로 내용을 검증합니다. 이미 같은 데이터가 있으면 건너뛰고, 일부만 차 있으면 아무것도 지우지 않고 중단합니다.

- Swagger: http://localhost:8080/swagger-ui.html
- RabbitMQ 관리 UI: http://localhost:15672 (DLQ 메시지 확인·재전송)
- 시드 계정 비밀번호는 모두 `test1234` (`seed/users.csv` 참고)
- 시드를 다시 넣으려면 `docker compose down -v` 후 다시 실행
- 시드 CSV(`seed/`)는 임시 데이터입니다. 수리이력은 경님, 계정은 주영님 데이터로 교체 예정

### DB와 데이터 (백엔드가 모두 띄움)

DB 구조와 데이터 적재는 백엔드가 책임지고, MCP 서버들은 조회 전용 계정으로 읽기만 합니다.

| 단계 | 내용 |
| --- | --- |
| Flyway V1·V2 | 업무 테이블 (요청·배정·검토·기체·수리이력 등) |
| Flyway V3 | RAG 테이블 10개 (세환님 `agent` 저장소 제공 SQL 그대로) |
| Flyway V4 | 조회 전용 계정 권한: `mro_reader`(rag-mcp, RAG 테이블만), `mro_business_reader`(business-mcp, 기체·수리이력·부서만) |
| SeedLoader | 업무 시드 `seed/*.csv` (local 프로파일) |
| RAG 데이터 | 세환님 전달본(`data/rag/2026-10-21-rag-v1/`, git 제외)을 `scripts/rag-restore.sh`로 복원. SDR 71,741건, 근거 107,137건, 임베딩 31,678개 |

조회 전용 계정 비밀번호는 Postgres 볼륨을 처음 만들 때 `docker/postgres/init`이 설정합니다 (`MRO_RAG_READER_PASSWORD`, `MRO_BUSINESS_READER_PASSWORD`, 기본값은 로컬 전용). 이미 볼륨이 있으면 `docker compose down -v` 후 다시 올려야 계정이 생깁니다.

### AI 서버 연결 (RabbitMQ)

```
mro.agent (direct) ── run.requested ──▶ mro.agent.run     Spring 발행 → AI 서버 소비 (TTL 5분)
                   └─ agent.event   ──▶ mro.agent.events  AI 서버 발행 → Spring 소비
mro.agent.dlx      ── run.dead      ──▶ mro.agent.run.dlq     Spring이 소비 → 사람 검토(SYSTEM_ERROR)
                   └─ event.dead    ──▶ mro.agent.events.dlq  보관 (관리 UI에서 확인·재전송)
```

- 큐·익스체인지는 Spring이 시작할 때 선언합니다. AI 서버는 선언하지 말고 소비·발행만 하세요.
- 실행 요청을 AI 서버가 `reject(requeue=false)`하거나 5분 안에 아무도 가져가지 않으면 실행 DLQ로 가고, 해당 요청은 사람 검토(SYSTEM_ERROR)로 넘어갑니다.
- 이벤트 처리 중 형식 오류·없는 요청은 바로, 그 밖의 오류는 3회 재시도 후 이벤트 DLQ로 갑니다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `AI_MODE` | local: `mock`, 그 외: `rabbit` | `mock`이면 앱 안의 `MockAiServer`가 실행 요청 큐를 소비해 키워드 기반 가짜 판단을 보냄. 실제 AI 서버와 동시에 켜지 마세요 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `mro` / `mro` | |
| `JWT_SECRET` | local 프로파일만 개발용 값 | 32바이트 이상. 그 밖의 프로파일에서는 없으면 시작하지 않음 |
| `CORS_ORIGINS` | `http://localhost:5173` | 쉼표로 여러 개 |
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
│   ├── auth        로그인, 내 정보
│   ├── user        계정 엔티티 (생성은 시드로만)
│   ├── master      기체·부서 마스터, 부서 목록 API
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
