# 데이터 복원 스크립트 공통 함수 (rag-restore.sh, tech-restore.sh에서 source).
# 컨테이너 안(RAG_RESTORE_DIRECT=1)에서는 PostgreSQL 클라이언트를 직접, 호스트에서는 postgres 컨테이너를 통해 실행합니다.

DB_USER="${DB_USERNAME:-mro}"
DB_NAME="${DB_NAME:-mro}"
LOG_TAG="${LOG_TAG:-restore}"

log() { printf '[%s] %s\n' "$LOG_TAG" "$*"; }
fail() { printf '[%s] 오류: %s\n' "$LOG_TAG" "$*" >&2; exit 1; }

pg() {
    if [ "${RAG_RESTORE_DIRECT:-0}" = "1" ]; then
        "$@"
    else
        docker compose exec -T postgres "$@"
    fi
}
psql_q() { pg psql -X -q -t -A -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -c "$1"; }
psql_file() { pg psql -X -q -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 "${@:2}" < "$1"; }

# 사용: check_sums 패키지폴더
check_sums() {
    if command -v sha256sum >/dev/null; then
        (cd "$1" && sha256sum -c --quiet SHA256SUMS) || fail "체크섬 불일치. 파일을 다시 받으세요."
    else
        (cd "$1" && shasum -a 256 -c --quiet SHA256SUMS) || fail "체크섬 불일치. 파일을 다시 받으세요."
    fi
}

# 사용: check_schema 패키지의SQL 마이그레이션파일 버전
# 패키지 SQL이 Flyway 파일(출처 주석 2줄 제외)과 같고, 해당 버전이 적용됐는지 확인합니다.
check_schema() {
    diff -q <(tail -n +3 "$2") "$1" >/dev/null \
        || fail "전달본 $(basename "$1")와 Flyway $(basename "$2")가 다릅니다. 덤프가 현재 스키마와 맞지 않을 수 있습니다."
    local applied
    applied="$(psql_q "select count(*) from flyway_schema_history where version = '$3' and success" 2>/dev/null || echo 0)"
    [ "$applied" = "1" ] || fail "Flyway V$3가 아직 적용되지 않았습니다. 먼저 백엔드를 한 번 실행하세요."
}

# 사용: count_state 테이블... → 전역 EMPTY, FILLED 설정
count_state() {
    EMPTY=0; FILLED=0
    local t n
    for t in "$@"; do
        n="$(psql_q "select count(*) from $t")"
        if [ "$n" = "0" ]; then EMPTY=$((EMPTY + 1)); else FILLED=$((FILLED + 1)); fi
    done
}

# 사용: restore_dump 덤프파일 — 한 트랜잭션, 실패하면 전부 롤백
restore_dump() {
    pg pg_restore -U "$DB_USER" -d "$DB_NAME" \
        --data-only --no-owner --no-acl --single-transaction --exit-on-error < "$1" \
        || fail "복원 실패. 트랜잭션이 롤백되어 테이블은 비어 있습니다."
}
