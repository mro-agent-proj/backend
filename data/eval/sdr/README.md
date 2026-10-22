# SDR 단위 평가셋 (sdr)

FAA 공개 SDR로 만든 단위 평가 데이터입니다. 전체 흐름이 아니라 **검색 도구, LLM1 분류, LLM2 부서 결정을 하나씩 따로** 평가합니다.

## 데이터 출처와 정답

- 출처: [FAA SDR 다운로드](https://www.faa.gov/av-info/download_SDR)의 `SDR-2019.csv`, `SDR-2020.csv` (받은 파일의 SHA256은 `manifest.json`).
- **누수 방지**: 검색 대상(RAG corpus)은 2021~2026년입니다. 2019~2020년만 쓰고, corpus와 신고 번호나 원문이 같은 것은 한 번 더 뺐습니다. 그래서 정답 사례가 검색 결과에 섞이지 않습니다.
- **정답 라벨**: 신고자가 붙인 JASC 코드 → ATA(앞 두 자리) → 부서(`data/seed/departments.csv`). 예: JASC `3231` → ATA `32` → `HYD`.
- 대상: Airbus A319·A320·A321 중 corpus에 있는 기종. 후보 14,315건에서 **부서 8개마다 같은 수**를 뽑았습니다(STR이 SDR의 절반 이상이라, 고르게 뽑지 않으면 STR만 맞혀도 점수가 높게 나옴). 한 부서 안에서도 JASC 코드를 돌아가며 뽑아 한 코드에 몰리지 않게 했습니다.
- 세 평가셋(search · triage · department)은 서로 겹치지 않습니다.

## 파일

| 파일 | 평가 대상 | 입력 | 정답 | 건수 |
| --- | --- | --- | --- | --- |
| `search.jsonl` | R01 `search_jasc_codes`, R02 `search_sdr_cases` | `query`: SDR 원문에서 매뉴얼·장 참조를 지운 영어 문장 | `expected.jasc`, `expected.ata` | 400 (부서별 50) |
| `triage.jsonl` | LLM1 1단계 (증상만 보고 ATA 후보 고르기) | `symptom_ko` / `symptom_en`: 증상만 남긴 한국어·영어 문장 | `expected.ata` | 200 (부서별 25) |
| `department.jsonl` | LLM2 부서 결정 (근거를 주고 부서 고르기) | 증상 + `evidence`: 고정된 SDR 근거 5건 | `expected.department` | 200 (부서별 25) |
| `jasc_to_department.jsonl` | JASC → 부서 변환 규칙 (회귀 테스트) | `jasc` | `expected_department` | 548 (전체 JASC) |
| `manifest.json` | 만든 날짜, 시드, 출처 해시, 필터, 제외 건수 | | | |

### 필드

```jsonc
// search.jsonl
{"id": "SEA-001", "aircraft_model": "A321231", "query": "AIRCRAFT WAS GROUNDED: AUTO THRUST FAILED ALL MODES. PERFORMED A AFS TEST ALL TESTS PASSED",
 "expected": {"jasc": "2230", "jasc_title": "AUTO THROTTLE SYSTEM", "ata": "22", "department": "AVI"},
 "source": {"file": "SDR-2020.csv", "record_no": 23393, "operator_control_number": "AALA202006229002"}}

// triage.jsonl  (department.jsonl은 여기에 evidence가 더해짐)
{"id": "TRI-131", "aircraft_model": "A319112",
 "symptom_ko": "DFW 이륙 후 LANDING GEAR DOORS NOT CLOSED ECAM 메시지가 표시됨.",
 "symptom_en": "LANDING GEAR DOORS NOT CLOSED ECAM message appeared after takeoff from DFW.",
 "expected": {"jasc": "3231", "jasc_title": "LANDING GEAR DOOR RETRACT SECTION", "ata": "32", "department": "HYD"}, "narrative_raw": "원문", "source": {...}}

// department.jsonl의 evidence (rag-mcp R02 응답과 같은 정보)
{"case_id": "SDR-...", "reported_jasc": "2121", "narrative": "원문 앞 400자", "rank": 0.42}
```

- `narrative_raw`는 참고용 원문입니다. 정답 단서(조치 내용, 매뉴얼 번호)가 들어 있으니 **모델 입력에 넣지 마세요.**
- `symptom_en`·`symptom_ko`는 `gpt-5.4-mini`로 원문에서 증상만 남겨 다시 쓴 것입니다. 조치·부품번호·매뉴얼·ATA 번호는 지우고, ECAM 메시지와 약어(APU, PACK, ENG 1 등)만 영어로 두었습니다. 한국어 문장의 영문 비율은 평균 13%입니다.

## 평가 방법 (권장 지표)

| 평가셋 | 방법 | 지표 |
| --- | --- | --- |
| search → R01 | `query`로 JASC 검색 | 정답 JASC가 상위 5개 안에 있는 비율 (recall@5) |
| search → R02 | 같은 기종으로 SDR 검색 (`aircraft_model`로 범위 고정) | 상위 5건 중 정답 ATA와 같은 사례 비율 (precision@5), 하나라도 있는 비율 (hit@5) |
| triage | LLM1 1단계를 `symptom_ko`로, 비교용으로 `symptom_en`으로 | 정답 ATA가 후보 안에 있는 비율, 1순위 정답률. 언어별 차이 |
| department | LLM2에 증상 + `evidence`를 주고 부서 결정 | 부서 정답률, 부서별 정답률(혼동 행렬) |
| jasc_to_department | backend·AI의 매핑 코드에 넣어 비교 | 548건 모두 일치해야 함 |

부서별로 같은 수라 전체 정답률이 곧 부서 평균 정답률입니다. 결과는 부서별로도 보세요(AVI·FCS처럼 SDR이 적은 부서가 약할 수 있음).

## 다시 만들기

```bash
# backend DB(corpus)가 떠 있어야 함. 번역에는 OpenAI 키 필요
LLM_API_KEY=... python3 scripts/build_sdr_eval.py
python3 scripts/build_sdr_eval.py --no-translate     # 번역 없이 (symptom_* 비움)
```

- FAA 원본은 `data/eval/.faa-cache/`에 받아 두고 git에는 넣지 않습니다.
- 시드가 고정되어 같은 FAA 파일이면 같은 문항이 뽑힙니다. FAA가 파일을 갱신하면 해시가 바뀌므로 `manifest.json`을 비교하세요.
- 번역 문장은 실행마다 조금씩 다를 수 있습니다. 비교 실험은 git에 있는 파일을 그대로 쓰세요.

## 한계

- 정답은 신고자가 붙인 JASC라 틀린 라벨이 섞여 있을 수 있습니다(SDR 원문과 JASC가 안 맞는 경우).
- `evidence`는 원문 전체 단어로 OR 검색해 뽑아서, 관련 없는 사례가 섞이기도 합니다. 실제 LLM2는 핵심어 2~5개로 검색하므로 실제 근거보다 질이 낮을 수 있습니다. 근거 묶음 자체보다는 "주어진 근거로 부서를 고르는 판단"을 보는 용도입니다.
- 영어 SDR을 다시 쓴 문장이라 실제 한국어 신고와 표현이 다릅니다. 실제 신고 문장으로 평가하려면 `scenario/`를 함께 보세요.
- 증상 재작성에서 조치 내용이 일부 남은 문항이 있을 수 있습니다(예: "교체함").
