#!/usr/bin/env bash
# RAG 데이터(세환님 전달본: SDR·AMM·JASC·임베딩) 초기 복원. 전달본 RESTORE.md 규칙을 따릅니다.
#
#   ./scripts/rag-restore.sh [패키지 폴더]     # 기본: data/rag-dump/sdr-jasc-amm_v1
#
# - 전달본이 없으면 경고만 남기고 건너뜁니다.
# - RAG 테이블 9개가 모두 비어 있을 때만 복원합니다. 같은 release가 있으면 검증 후 건너뜁니다.
# - 일부만 차 있거나 다른 release가 있으면 아무것도 지우지 않고 중단합니다.
set -euo pipefail
cd "$(dirname "$0")/.."
LOG_TAG=rag-restore
source scripts/lib/restore-common.sh

PACKAGE_DIR="${1:-data/rag-dump/sdr-jasc-amm_v1}"
if [ ! -d "$PACKAGE_DIR" ]; then
    log "경고: 전달본이 없어 복원을 건너뜁니다 ($PACKAGE_DIR). RAG 데이터는 미준비 상태입니다."
    log "      팀 공유 드라이브의 전달본을 data/rag-dump/ 아래에 두고 다시 실행하세요."
    exit 0
fi
DUMP="$(ls "$PACKAGE_DIR"/*.dump 2>/dev/null | head -1)"
[ -n "$DUMP" ] || fail "덤프 파일(*.dump)이 없습니다: $PACKAGE_DIR"

log "1/5 파일 무결성 확인 (SHA256SUMS)"
check_sums "$PACKAGE_DIR"

log "2/5 스키마 호환 확인 (전달본 V3 = Flyway V3)"
check_schema "$PACKAGE_DIR/V3__rag_evidence.sql" src/main/resources/db/migration/V3__rag_evidence.sql 4

RELEASE_KEY="$(grep -o '"release_key": *"[^"]*"' "$PACKAGE_DIR/manifest.json" | head -1 | sed 's/.*: *"\(.*\)"/\1/')"
[ -n "$RELEASE_KEY" ] || fail "manifest.json에서 release_key를 찾지 못했습니다."

log "3/5 현재 RAG 테이블 상태 확인 (release: $RELEASE_KEY)"
count_state public.knowledge_releases public.jasc_codes public.sdr_cases public.amm_references public.amm_chunks \
    public.amm_chunk_references public.evidence public.knowledge_indexes rag.amm_embeddings

if [ "$FILLED" -gt 0 ]; then
    EXISTING="$(psql_q "select string_agg(release_key, ',') from public.knowledge_releases")"
    if [ "$EMPTY" -eq 0 ] && [ "$EXISTING" = "$RELEASE_KEY" ]; then
        log "같은 release가 이미 있습니다. 내용만 검증합니다."
        psql_file "$PACKAGE_DIR/verify.sql" >/dev/null 2>&1 \
            || fail "기존 RAG 데이터가 전달본과 다릅니다. 자동으로 덮어쓰지 않습니다."
        log "검증 통과. 복원을 건너뜁니다."
        exit 0
    fi
    fail "RAG 테이블이 비어 있지 않습니다 (데이터 있음 ${FILLED}개, 비어 있음 ${EMPTY}개, release: ${EXISTING:-없음}). 자동으로 지우지 않습니다. 로컬이라면 docker compose down -v 후 다시 시작하세요."
fi

log "4/5 덤프 복원 (한 트랜잭션, 수십 초 걸릴 수 있음)"
restore_dump "$DUMP"

log "5/5 복원 결과 검증 (verify.sql)"
psql_file "$PACKAGE_DIR/verify.sql" 2>/dev/null | grep PASS || fail "검증 실패. 복원된 데이터가 전달본과 다릅니다."
log "완료."
