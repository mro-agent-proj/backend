# 접수 검증

현재 평가는 [서비스 평가셋 200건](../service/README.md)입니다. 이 폴더는 접수 단계의 422 검증만 다룹니다.

| 파일 | 내용 |
| --- | --- |
| `validation.jsonl` | 기체·항공사·기종·필수값·권한의 422 응답 검증 6건 |
| `scenarios.md` | 초기 시연 시나리오 설명 (참고용, 현재 시드의 기대 결과로 쓰지 않음) |

```bash
python3 scripts/scenario_eval.py --only V1,V2,V3,V4,V5,V6
```

실제 Backend에 접수하므로 스택이 떠 있어야 합니다. 평가 조건과 채점 정의는 서비스 README를 따릅니다.
