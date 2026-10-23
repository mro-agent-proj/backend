# 접수 검증과 과거 23건 시나리오

현재 실행 대상은 [서비스 평가셋 200건](../service/README.md)입니다. 이 폴더의 과거 S01~S23 실행용 `scenarios.jsonl`은 삭제됐습니다.

| 파일 | 상태 |
| --- | --- |
| `validation.jsonl` | 현재 사용: 기체·항공사·기종·필수값·권한의 422 응답 검증 6건 |
| `scenarios.md` | 과거 시연 설명 보존. 현재 v2 시드의 근거 ID·기대 결과로 재사용하지 않음 |
| `results/` | 과거 평가 결과 저장 위치(git 제외); 현재 기본 결과는 service/results |

```bash
python3 scripts/scenario_eval.py --only V1,V2,V3,V4,V5,V6
```

실행은 실제 Backend에 접수합니다. 평가 조건·비용·현재 채점 정의는 서비스 README를 따릅니다. 과거 결과를 현재 버전의 성능으로 표시하지 않습니다.
