# 서비스 통합 평가 200건

`scenarios.jsonl`과 업무 시드 `../../seed/maintenance_history_v2.csv`를 함께 사용합니다. `manifest.json`에 파일 SHA256, 시나리오 200건·수리이력 1,600건을 기록합니다. 파일 변경 시 manifest도 검토·갱신해야 CI 계약 검사가 통과합니다. manifest는 **파일 조합**을 식별하며 실행 DB의 동일성을 보장하지 않습니다.

| 묶음 | 건수 | 목적 |
| --- | --- | --- |
| A | 64 | 동일 기체 반복 고장 |
| B | 96 | 신규 증상 |
| C | 16 | 다른 기체 유사 사례 |
| D | 24 | 동일 ATA·다른 JASC 함정 |

- dev 80 / test 120. 기대 배정 197 / 사람 검토 3.
- 라벨: `not_reviewed` 161 / `needs_final_review` 24 / `decided` 15. 평가 라벨과의 일치를 실제 전문가 판정과 동일시하지 않습니다.
- `expected`, `source`, `label_check`는 평가 메타데이터입니다. 접수에는 기체번호·항공사·기종·증상 4개 필드만 보냅니다.

## 저장된 실행 결과 (2026-10-23)

[deliverables 평가 번들](https://github.com/mro-agent-proj/deliverables/tree/main/evaluations/2026-10-23-isolated-200)에
실제 LLM·HTTP MCP·Docker DB로 실행한 200건의 결과·감사 기록·체크섬을 보관합니다.
2026-10-26 게시 정리이며 새 평가가 아닙니다. 보고서와 원본 데이터셋은 이 변경으로 수정하지 않습니다.

결과 재집계는 [LLM 오프라인 도구](https://github.com/mro-agent-proj/LLM/tree/main/scripts/evaluation)를 사용합니다.
`--dataset`으로 이 폴더의 `scenarios.jsonl`을 지정하면 실행 당시 SHA256과 라벨·금지 이력을 비교합니다.
전체 통과·자동배정 통과·LLM2 부서 일치의 분모는 번들의 `summary.json`을 기준으로 봅니다.
시드 파일과 실제 DB 대조 기록도 번들에 포함하며, manifest만으로 DB 적재 상태를 보장하지 않습니다.

200건 실행 이후 보완된 trace 조회 실패·FINAL 누락 채점 회귀 테스트와,
실모델 200건 실행 결과를 구분합니다. 보완 후 200건을 새로 실행했다고 해석하지 않습니다.

## 실행

Backend `AI_MODE=rabbit`, 실제 MCP 두 개와 AI 서버가 필요합니다. 요청이 DB에 쌓이고 모델 비용이 발생합니다. 단일 AI 소비자(prefetch=1)에서는 기본 `--workers 1`로 순차 실행합니다. 동시 접수 수를 높이면 큐 대기로 TTL/평가 시간 제한에 걸릴 수 있습니다.

```bash
python3 scripts/scenario_eval.py --workers 1
python3 scripts/scenario_eval.py --split test --workers 1
python3 scripts/scenario_eval.py --only SV-A-001,SV-B-002,V2
python3 -m unittest discover -s scripts/tests -v  # 외부 호출 없는 채점 검증
```

기본 결과는 `data/eval/service/results/<시각>.json`에 저장합니다. 접수 검증은 `../scenario/validation.jsonl` 6건을 사용하며 `--no-validation`으로 생략할 수 있습니다.

`SeedLoader`는 users가 있으면 업무 시드를 건너뜁니다. 소스 CSV가 최신이어도 기존 DB는 이전 이력일 수 있습니다. 평가 전 DB의 이력 내용·버전을 대조하세요. 기존 DB·큐를 삭제하지 않고 별도 빈 평가 환경이나 Backend 담당자의 갱신 절차를 사용합니다.

## 채점 정의

- 배정 기대: 상태 ASSIGNED, 허용 부서 일치, `must_not_cite` 인용 없음이 모두 필요합니다.
- 사람 검토 기대: 상태 HUMAN_REVIEW이면 통과합니다. 세부 보류 사유의 적절성까지 판정한 것은 아닙니다.
- 채점에는 성공한 trace 조회와 FINAL 판단이 필요하며, 배정에는 FINAL 근거 목록도 필요합니다. 정상 HUMAN_REVIEW 완료는 AI 계약에 따라 근거 목록을 생략할 수 있습니다. 조회 실패·잘못된 응답·FINAL 누락은 `evaluation_error`로 남기고 통과시키지 않습니다. 관측된 ASSIGNED는 자동배정 분모에 유지하며, 이때 정밀도는 확인 가능한 성공만 센 보수적 수치입니다. 오류 건수를 확인하고 정상 추적 자료로 재평가해야 모델 오답과 구분할 수 있습니다.
- 자동배정 정밀도: 통과한 자동배정 / **전체 자동배정**. 사람 검토가 기대됐는데 배정한 사례도 분모 및 실패에 포함합니다.
- 오배정률: 배정 기대 문항 중 허용 부서를 틀리게 배정한 비율입니다. 부서가 맞아도 금지 이력을 인용한 사례는 별도 집계합니다. 두 결함은 겹칠 수 있습니다.
- `배정 평가 통과율 (부서·근거 조건)`은 이전 `부서 정답률 (배정 기대 문항 중)`을 대체합니다. 부서명만 비교한 정확도가 아닙니다.
- 사람 검토로 간 사례가 불필요한 보류였는지는 추가 판정이 필요합니다. 정답 부서를 예측한 보류를 자동배정 성공에 더하지 않습니다.
- dev/test 결과를 구분합니다. 반복 실행으로 테스트셋까지 튜닝했다면 최종 확인에는 새 미사용 자료가 필요합니다.
