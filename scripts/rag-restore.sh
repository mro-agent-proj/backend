#!/usr/bin/env bash
# RAG 데이터 초기 복원 (세환님 전달본 RESTORE.md 규칙).
#
#   ./scripts/rag-restore.sh [패키지 폴더]     # 기본: data/rag/2026-10-21-rag-v1
#
# 순서: docker compose up -d → ./gradlew bootRun (Flyway V1~V4) → 이 스크립트 → MCP 서버 시작
# - RAG 테이블 9개가 모두 비어 있을 때만 복원합니다.
# - 같은 release가 이미 있으면 검증만 하고 건너뜁니다.
# - 일부만 차 있거나 다른 release가 있으면 아무것도 지우지 않고 중단합니다.
# - pg_restore는 버전이 맞는 postgres 컨테이너 안의 클라이언트로, 한 트랜잭션에서 실행합니다.
#   실패하면 전부 롤백되므로 고친 뒤 다시 실행하면 됩니다.
set -euo pipefail

cd "$(dirname "$0")/.."
PACKAGE_DIR="${1:-data/rag/2026-10-21-rag-v1}"
DB_USER="${DB_USERNAME:-mro}"
DB_NAME="${DB_NAME:-mro}"

log() { printf '[rag-restore] %s\n' "$*"; }
fail() { printf '[rag-restore] 오류: %s\n' "$*" >&2; exit 1; }

psql_q() {
    docker compose exec -T postgres psql -X -q -t -A -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -c "$1"
}

[ -d "$PACKAGE_DIR" ] || fail "패키지 폴더가 없습니다: $PACKAGE_DIR (세환님 전달본을 data/rag/ 아래에 두세요)"
DUMP="$(ls "$PACKAGE_DIR"/*.dump 2>/dev/null | head -1)"
[ -n "$DUMP" ] || fail "덤프 파일(*.dump)이 없습니다: $PACKAGE_DIR"

log "1/5 파일 무결성 확인 (SHA256SUMS)"
if command -v sha256sum >/dev/null; then
    (cd "$PACKAGE_DIR" && sha256sum -c --quiet SHA256SUMS) || fail "체크섬 불일치. 파일을 다시 받으세요."
else
    (cd "$PACKAGE_DIR" && shasum -a 256 -c --quiet SHA256SUMS) || fail "체크섬 불일치. 파일을 다시 받으세요."
fi

log "2/5 스키마 호환 확인 (전달본 V3 = Flyway V3)"
diff -q <(tail -n +3 src/main/resources/db/migration/V3__rag_evidence.sql) "$PACKAGE_DIR/V3__rag_evidence.sql" >/dev/null \
    || fail "전달본 V3와 Flyway V3가 다릅니다. 이 덤프는 현재 스키마와 호환되지 않을 수 있습니다."
APPLIED="$(psql_q "select count(*) from flyway_schema_history where version = '4' and success" 2>/dev/null || echo 0)"
[ "$APPLIED" = "1" ] || fail "Flyway V4가 아직 적용되지 않았습니다. 먼저 ./gradlew bootRun으로 Spring을 한 번 실행하세요."

RELEASE_KEY="$(grep -o '"release_key": *"[^"]*"' "$PACKAGE_DIR/manifest.json" | head -1 | sed 's/.*: *"\(.*\)"/\1/')"
[ -n "$RELEASE_KEY" ] || fail "manifest.json에서 release_key를 찾지 못했습니다."

log "3/5 현재 RAG 테이블 상태 확인 (release: $RELEASE_KEY)"
TABLES="public.knowledge_releases public.jasc_codes public.sdr_cases public.amm_references public.amm_chunks public.amm_chunk_references public.evidence public.knowledge_indexes rag.amm_embeddings"
EMPTY=0; FILLED=0
for t in $TABLES; do
    n="$(psql_q "select count(*) from $t")"
    if [ "$n" = "0" ]; then EMPTY=$((EMPTY + 1)); else FILLED=$((FILLED + 1)); fi
done

if [ "$FILLED" -gt 0 ]; then
    EXISTING="$(psql_q "select string_agg(release_key, ',') from public.knowledge_releases")"
    if [ "$EMPTY" -eq 0 ] && [ "$EXISTING" = "$RELEASE_KEY" ]; then
        log "같은 release가 이미 있습니다. 내용만 검증합니다."
        docker compose exec -T postgres psql -X -q -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
            < "$PACKAGE_DIR/verify.sql" >/dev/null || fail "기존 RAG 데이터가 전달본과 다릅니다. 자동으로 덮어쓰지 않습니다."
        log "검증 통과. 복원을 건너뜁니다."
        exit 0
    fi
    fail "RAG 테이블이 비어 있지 않습니다 (데이터 있음 ${FILLED}개, 비어 있음 ${EMPTY}개, release: ${EXISTING:-없음}). 자동으로 지우지 않습니다. 로컬이라면 docker compose down -v 후 다시 시작하세요."
fi

log "4/5 덤프 복원 (한 트랜잭션, 수 분 걸릴 수 있음)"
docker compose exec -T postgres pg_restore -U "$DB_USER" -d "$DB_NAME" \
    --data-only --no-owner --no-acl --single-transaction --exit-on-error < "$DUMP" \
    || fail "복원 실패. 트랜잭션이 롤백되어 RAG 테이블은 비어 있습니다."

log "5/5 복원 결과 검증 (verify.sql)"
docker compose exec -T postgres psql -X -q -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
    < "$PACKAGE_DIR/verify.sql" | grep PASS || fail "검증 실패. 복원된 데이터가 전달본과 다릅니다."
log "완료. 이제 MCP 서버를 시작하세요."
