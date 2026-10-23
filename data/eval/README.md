# 평가셋 (eval)

| 폴더 | 평가 범위 | 건수 | 실행 |
| --- | --- | --- | --- |
| [service](service/README.md) | 실제 접수부터 배정·사람 검토까지 | 200 (dev 80 / test 120) | `python3 scripts/scenario_eval.py --workers 1` |
| [scenario](scenario/README.md) | 접수 단계 422 검증 및 과거 시나리오 설명 | validation 6 | 기본 평가 뒤 실행; 과거 23건 JSONL은 삭제됨 |
| [sdr](sdr/README.md) | 검색·LLM 분류·부서 결정의 개별 평가 | 검색 400 / 분류 200 / 부서 200 / 매핑 548 | 폴더 README 참고 |

서비스 평가는 `maintenance_history_v2.csv` 1,600건과 짝을 이룹니다. `service/manifest.json`은 정확한 파일 해시와 수량을 기록합니다. CI는 파일 조합을 검사하지만 실행 중인 DB 내용을 자동 확인하지는 않습니다.

서비스 평가에는 목업 이력 기반 사례가 포함됩니다. 과거 SDR 단위 평가와 입력·경로가 다르므로 결과를 같은 정확도로 비교하지 않습니다. 두 평가 모두 실제 항공사 운영 안전성의 인증 자료가 아닙니다.
