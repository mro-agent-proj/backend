#!/usr/bin/env bash
# 공유 DB에 전달받은 데이터를 모두 복원합니다 (각 스크립트는 전달본이 없으면 건너뜀).
# docker compose의 rag-restore 서비스가 백엔드 기동 뒤 이 스크립트를 실행합니다.
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/rag-restore.sh
bash scripts/tech-restore.sh
bash scripts/sdr-embed-restore.sh
