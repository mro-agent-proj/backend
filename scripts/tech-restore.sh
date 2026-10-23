#!/usr/bin/env bash
# R04 기술자료(Airbus Safety First) 복원. v2: 문서·조각은 경님 tech-v1 그대로, 임베딩은 OpenAI 1024차원(Flyway V8).
#
#   ./scripts/tech-restore.sh [패키지 폴더]     # 기본: data/rag-dump/airbus-safety-first_v2
#
# - 전달본이 없으면 경고만 남기고 건너뜁니다.
# - 테이블 3개가 모두 비어 있을 때만 복원합니다. 이미 같은 데이터가 있으면 검증 후 건너뜁니다.
# - V8 업그레이드: 기존 DB는 V8이 tech_embeddings만 새로 만들어 문서·조각은 남고 임베딩만 비어 있습니다.
#   문서·조각이 전달본과 같으면 그대로 두고 임베딩만 덤프에서 복원합니다.
# - 그 밖에 일부만 차 있거나 내용이 다르면 아무것도 지우지 않고 중단합니다.
# - 검증: verify.sql 결과(건수·내용 해시·원문 위치 불일치 수)를 manifest.json의 값과 비교합니다.
# - 마지막에 문서별 담당 부서를 현재 departments 테이블로 다시 계산합니다 (lib/tech-departments.sql).
set -euo pipefail
cd "$(dirname "$0")/.."
LOG_TAG=tech-restore
source scripts/lib/restore-common.sh

PACKAGE_DIR="${1:-data/rag-dump/airbus-safety-first_v2}"
if [ ! -d "$PACKAGE_DIR" ]; then
    log "경고: 전달본이 없어 복원을 건너뜁니다 ($PACKAGE_DIR). 기술자료(R04)는 미준비 상태입니다."
    exit 0
fi
DUMP="$(ls "$PACKAGE_DIR"/*.dump 2>/dev/null | head -1)"
[ -n "$DUMP" ] || fail "덤프 파일(*.dump)이 없습니다: $PACKAGE_DIR"

# manifest의 counts·content_md5를 verify.sql 출력과 같은 "테이블|건수|해시" 형식으로 만듭니다 (psql로 JSON 파싱).
expected() {
    pg psql -X -q -t -A -F '|' -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
        -v manifest="$(cat "$PACKAGE_DIR/manifest.json")" <<'SQL'
select k, :'manifest'::jsonb -> 'counts' ->> k, coalesce(:'manifest'::jsonb -> 'content_md5' ->> k, '')
from unnest(array['tech_documents', 'tech_chunks', 'tech_embeddings', 'offset_mismatch']) k order by k;
SQL
}
actual() { psql_file "$PACKAGE_DIR/verify.sql" -t -A -F '|' | grep -v '^$' | sort; }
verify() { [ "$(expected | sort)" = "$(actual)" ]; }
# 문서·조각·원문 위치만 비교 (임베딩 제외)
docs_match() {
    local keep='^(tech_documents|tech_chunks|offset_mismatch)[|]'
    [ "$(expected | grep -E "$keep" | sort)" = "$(actual | grep -E "$keep")" ]
}
restore_embeddings_only() {
    pg pg_restore -U "$DB_USER" -d "$DB_NAME" --data-only --no-owner --no-acl --single-transaction \
        --exit-on-error -n rag -t tech_embeddings < "$1" \
        || fail "임베딩 복원 실패. 트랜잭션이 롤백되어 rag.tech_embeddings는 비어 있습니다."
}
sync_departments() {
    log "6/6 문서별 담당 부서를 현재 부서 매핑으로 맞춤"
    psql_file scripts/lib/tech-departments.sql
}

log "1/6 파일 무결성 확인 (SHA256SUMS)"
check_sums "$PACKAGE_DIR"

log "2/6 스키마 호환 확인 (전달본 V5·V8 = Flyway V5·V8)"
check_schema "$PACKAGE_DIR/V5__rag_tech_docs.sql" src/main/resources/db/migration/V5__rag_tech_docs.sql 5
check_schema "$PACKAGE_DIR/V8__drop_amm_semantic_openai_tech.sql" src/main/resources/db/migration/V8__drop_amm_semantic_openai_tech.sql 8

log "3/6 현재 기술자료 테이블 상태 확인"
count_state public.tech_documents public.tech_chunks rag.tech_embeddings
if [ "$FILLED" -gt 0 ]; then
    if [ "$EMPTY" -eq 0 ] && verify; then
        log "같은 데이터가 이미 있습니다. 복원을 건너뜁니다."
        sync_departments
        exit 0
    fi
    if [ "$(psql_q "select count(*) from rag.tech_embeddings")" = "0" ] && docs_match; then
        log "4/6 V8 업그레이드: 문서·조각은 전달본과 같아 그대로 두고 임베딩만 복원"
        restore_embeddings_only "$DUMP"
        log "5/6 복원 결과 검증 (verify.sql = manifest)"
        if ! verify; then
            printf '기대값:\n%s\n실제값:\n%s\n' "$(expected | sort)" "$(actual)" >&2
            fail "검증 실패. 복원된 임베딩이 manifest와 다릅니다."
        fi
        log "PASS: 기존 문서·조각 유지, OpenAI 임베딩 복원, manifest와 일치"
        sync_departments
        log "완료."
        exit 0
    fi
    fail "기술자료 테이블이 비어 있지 않고 전달본과 다릅니다 (데이터 있음 ${FILLED}개, 비어 있음 ${EMPTY}개). 자동으로 지우지 않습니다. 로컬이라면 docker compose down -v 후 다시 시작하세요."
fi

log "4/6 덤프 복원 (한 트랜잭션)"
restore_dump "$DUMP"

log "5/6 복원 결과 검증 (verify.sql = manifest)"
if ! verify; then
    printf '기대값:\n%s\n실제값:\n%s\n' "$(expected | sort)" "$(actual)" >&2
    fail "검증 실패. 복원된 데이터가 manifest와 다릅니다."
fi
log "PASS: 문서 61편·조각 496개·임베딩 496개가 manifest와 일치, 원문 위치 불일치 0"
sync_departments
log "완료."
