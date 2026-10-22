# 평가셋 (eval)

AI가 정비 요청을 맞는 부서로 보내는지 실제 스택에서 확인하는 시나리오입니다. 시드(`../seed/`)의 기체·수리이력에 맞춰 만든 목업이며, 실제 항공사 데이터의 분류 정확도를 뜻하지 않습니다.

| 파일 | 내용 |
| --- | --- |
| `scenarios.md` | 사람이 읽는 원본 표. 증상·기대 경로·기대 부서·근거 이력 |
| `scenarios.jsonl` | 위 표 1절(AI 경로 23건)을 프로그램용으로 바꾼 것 |
| `validation.jsonl` | 위 표 2절(접수 단계 422 검증 6건) |
| `results/` | `scripts/eval.py` 실행 결과 (git에 넣지 않음) |

## 시나리오 구성 (23건)

| 기대 경로 | 건수 | 시나리오 | 맞는 결과 |
| --- | --- | --- | --- |
| `DIRECT` (LLM1이 같은 기체 이력으로 바로 배정) | 7 | S01~S06, S23 | 기대 부서로 ASSIGNED |
| `INVESTIGATE` (LLM2 조사 → LLM3 검토 → 배정) | 11 | S07~S15, S21·S22 | 기대 부서로 ASSIGNED |
| `HUMAN_REVIEW` (후보가 여럿인 모호한 증상) | 5 | S16~S20 | HUMAN_REVIEW |

- S21·S22는 LLM1 함정입니다. 같은 ATA지만 다른 JASC인 이력(`must_not_cite`)을 근거로 인용하면 부서가 맞아도 오답입니다.
- `history_evidence`는 정답 근거 이력이며 채점에는 쓰지 않습니다(참고용).

`scenarios.jsonl` 한 줄 예:

```json
{"id": "S23", "category": "반복 결함 (비행 조종)", "tail_number": "HL7713", "airline_code": "KAL", "aircraft_model": "A321-211",
 "symptom_text": "접근 중 플랩을 CONF 1로 내리자 ...", "expected": {"status": "ASSIGNED", "department": "FCS", "candidates": ["FCS"],
 "path": "DIRECT", "history_evidence": ["MH-001567", "MH-001574"]}, "note": "..."}
```

## 실행

backend를 `AI_MODE=rabbit`으로 띄우고 MCP 두 개와 AI 서버가 떠 있어야 합니다. 통합 레포에서는 `./scripts/up.sh`.

```bash
python3 scripts/eval.py                      # 23 + 6건, 결과는 data/eval/results/<시각>.json
python3 scripts/eval.py --only S01,S10,V2    # 일부만
```

- 실행할 때마다 요청이 DB에 쌓이고 OpenAI 비용이 듭니다(23건 약 6분). 시연 데이터를 깨끗하게 두려면 평가 후 정리하거나 `docker compose down -v`로 새로 시작하세요.
- 신고자는 기체 항공사에 맞춰 `reporter1@{kal,aar,jna}.test`, 판단 경로·근거는 `admin@mro.test`로 조회합니다.
- 같은 시나리오도 실행마다 판단이 달라질 수 있습니다. 비교할 때는 여러 번 돌려 비율로 보세요.

## 출력 요약 항목

| 항목 | 뜻 |
| --- | --- |
| 배정 정답 | ASSIGNED가 기대인 18건 중 기대 부서로 배정된 수 (함정 이력 인용 시 오답) |
| 사람 검토 전환 | 모호한 증상 5건 중 HUMAN_REVIEW로 간 수 |
| 경로 일치 | DIRECT / INVESTIGATE / HUMAN_REVIEW가 기대와 같은 수 |
| 자동 배정률 | 전체 중 ASSIGNED 비율 |
| 평균 소요 | 접수부터 최종 상태까지 (초) |
| 접수 검증 일치 | V1~V6의 422 응답 field·issue가 기대와 같은 수 |

최종보고서(2026-10-21, `gpt-5.4-mini`) 기준 결과: 배정 정답 14/18, 자동 배정률 91%, 평균 14.6초, 접수 검증 6/6.
