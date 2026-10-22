# data

| 폴더 | git | 내용 | 쓰는 곳 |
| --- | --- | --- | --- |
| `seed/` | 있음 | 업무 시드 CSV: 항공사 3, 기체 20, 부서 8, 계정 15, 수리이력 1,577 | 빈 DB일 때 SeedLoader가 적재 |
| `eval/scenario/` | 있음 | 통합 평가셋: 손으로 만든 한국어 시나리오 23건 + 접수 검증 6건 ([README](eval/scenario/README.md)) | `scripts/scenario_eval.py` |
| `eval/sdr/` | 있음 | 단위 평가셋: FAA SDR 2019~2020 기반 검색 400 · LLM1 분류 200 · LLM2 부서 200 · JASC 매핑 548 ([README](eval/sdr/README.md)) | `scripts/build_sdr_eval.py`로 생성 |
| `rag-dump/sdr-jasc-amm_v1/` | 없음 | RAG 복원용 덤프: SDR 71,741 · JASC 548 · AMM 참조 35,396 · 근거 107,137 · 임베딩 31,678 | `scripts/rag-restore.sh` (compose `rag-restore`가 자동 실행) |
| `rag-dump/sdr-embeddings_v1/` | 없음 | R02 하이브리드 검색용 SDR 원문 임베딩 71,741건 (OpenAI text-embedding-3-large, 1024차원) | `scripts/sdr-embed-restore.sh` (Flyway V7 필요) |
| `rag-dump/airbus-safety-first_v1/` | 없음 | 기술자료 복원용 덤프: Airbus Safety First 61편, 조각·임베딩 496 | `scripts/tech-restore.sh` |
| `rag-csv/` | 없음 | 위 덤프 내용을 사람이 보는 CSV로 뽑은 것: `sdr-by-year/`(SDR 원본 연도별), `db-tables/`(테이블별) | 확인·공유용, 서비스는 안 씀 |

git에 없는 폴더는 [통합 레포](https://github.com/mro-agent-proj/mro-agent-integration)에서 `./scripts/fetch-data.sh`로 받습니다.
