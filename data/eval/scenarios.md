# 시드 시나리오 (통합 테스트 케이스)

- 데이터: 에어버스 A320 계열 기체 20대·항공사 3곳(KAL·AAR·JNA, 팀 시드와 같은 코드), 수리이력 1,577건(2024-01-16 ~ 2026-10-19, 2024년 20건·2025년 298건·2026년 1,259건, 이 중 ATA 27 비행 조종 78건). FAA SDR 실제 결함을 바탕으로 한국어로 다시 쓴 목업이며, 규약 필드에 SDR의 `jasc_code`(4자리)를 더했습니다.
- 시연 기준일 2026-10-21. 요청은 이 날짜에 접수한다고 보고, 반복 결함 근거 이력은 그 직전 30일 안에 있습니다.

## 1. AI 경로 시나리오 (23건: LLM1 7 · LLM2 배정 11 · 사람 검토 5)

| # | 유형 | 등록부호 | 항공사 / 모델 | 입력할 증상 | 기대 경로 | 기대 부서 | 근거 이력 |
|---|---|---|---|---|---|---|---|
| S01 | 반복 결함 | HL7701 | KAL / A320-232 | 상승·강하할 때 조종실과 앞쪽 객실에서 퀴퀴한 양말 냄새가 계속 남. 연기나 연무는 보이지 않음. | CLASSIFYING → sufficient=True → 배정 실행 → ASSIGNED | SYS | MH-001375 |
| S02 | 반복 결함 | HL7702 | AAR / A321-253N | 상승 중 8,000ft를 지날 때 ECAM DOOR L AFT EMER EXIT 경고가 떴다가 몇 초 만에 사라짐. | CLASSIFYING → sufficient=True → 배정 실행 → ASSIGNED | STR | MH-001453 |
| S03 | 반복 결함 | HL7703 | JNA / A320-212 | 부기장 산소마스크 마이크가 작동하지 않음. 인터폰 테스트 때 음성이 전달되지 않음. | CLASSIFYING → sufficient=True → 배정 실행 → ASSIGNED | AVI | MH-001487 |
| S04 | 반복 결함 | HL7704 | KAL / A321-271N | 순항 FL350에서 APU 시동을 두 번 시도했는데 모두 실패함 (외기 -44℃). | CLASSIFYING → sufficient=True → 배정 실행 → ASSIGNED | ENG | MH-001498 |
| S05 | 반복 결함 | HL7705 | AAR / A320-214 | 2번 승무원석 손전등이 켜지지 않음. | CLASSIFYING → sufficient=True → 배정 실행 → ASSIGNED | CAB | MH-001482 |
| S06 | 반복 결함 | HL7706 | JNA / A320-212 | 게이트에서 APU 블리드를 켜면 객실에 퀴퀴한 양말 냄새가 남. | CLASSIFYING → sufficient=True → 배정 실행 → ASSIGNED | ENG | MH-001483 |
| S07 | 이력 없는 신규 증상 | HL7711 | AAR / A319-112 | 이륙 회전 때 가속이 둔했고, 상승 중 플라이트 디렉터가 기수를 과하게 들도록 지시함. | CLASSIFYING → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | AVI | 같은 기체 관련 이력 없음 |
| S08 | 이력 없는 신규 증상 | HL7719 | KAL / A319-112 | 지상 이동 후 엔진을 끌 때 싱글 차임과 함께 비상구 표시등이 켜짐. 지상 전원으로 바꿀 때도 같은 현상. | CLASSIFYING → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | ELE | 같은 기체 관련 이력 없음 |
| S09 | 이력 없는 신규 증상 | HL7709 | JNA / A321-231 | 순항 중 ECAM ENG 2 OIL LO PR 경고가 잠깐 떴다가 사라짐. 오일 압력 지시가 순간적으로 떨어짐. | CLASSIFYING → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | ENG | 같은 기체 관련 이력 없음 |
| S10 | 이력 없는 신규 증상 | HL7717 | AAR / A321-231 | 착륙 후 지상 이동 중 브레이크를 밟을 때마다 기체 전체에 반복적인 진동과 덜컹거림이 느껴짐. | CLASSIFYING → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | HYD | 같은 기체 관련 이력 없음 |
| S11 | 이력 없는 신규 증상 | HL7714 | AAR / A319-132 | 순항 중 연료 점검에서 좌우 탱크 연료량이 약 1,000lbs 차이 남. | CLASSIFYING → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | SYS | 같은 기체 관련 이력 없음 |
| S12 | 이력 없는 신규 증상 | HL7707 | KAL / A320-214 | 접근 중 5,000ft부터 ECAM DOOR R AFT EMER EXIT(날개 위 비상구) 경고가 착륙까지 6번 반복되고 게이트에서도 계속 표시됨. | CLASSIFYING → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | STR | 같은 기체 관련 이력 없음 |
| S13 | 이력 없는 신규 증상 | HL7708 | AAR / A320-232 | 후방 갤리 2번 오븐이 작동하지 않고 오븐 차단기가 반복해서 트립됨. | CLASSIFYING → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | CAB | 같은 기체 관련 이력 없음 |
| S14 | 다른 기체 유사 사례 | HL7716 | KAL / A320-232 | 접근 속도로 줄일 때 기체 앞쪽에서 퀴퀴한 양말 냄새. PACK 1을 끄자 냄새가 사라짐. | CLASSIFYING → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | SYS | HL7701의 MH-001246 (search_similar_history) |
| S15 | 다른 기체 유사 사례 | HL7718 | JNA / A321-271N | 상승 중과 순항 중 한 번씩 ENG 1 FIRE LOOP A 결함 메시지가 나타남. | CLASSIFYING → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | SYS | HL7704의 MH-001152 (search_similar_history) |
| S16 | 모호한 증상 | HL7710 | KAL / A321-271N | 강하 중 PACK에서 퀴퀴한 냄새가 나서 점검하니 우측 주착륙장치 휠웰부터 APU 흡입구까지 유압유 자국이 있음. | REVIEWING(SUPPLEMENT ×2) → HUMAN_REVIEW(RETRY_LIMIT) 또는 REVIEWING(HUMAN) → HUMAN_REVIEW(AGENT_NEEDS_HUMAN) | 후보 ENG / HYD / SYS (확정 안 됨) | 같은 기체 관련 이력 없음 |
| S17 | 모호한 증상 | HL7712 | JNA / A320-271N | APU 시동 직후 PACK 2 OVHT 경고와 함께 객실에 탄 냄새가 남. | REVIEWING(SUPPLEMENT ×2) → HUMAN_REVIEW(RETRY_LIMIT) 또는 REVIEWING(HUMAN) → HUMAN_REVIEW(AGENT_NEEDS_HUMAN) | 후보 SYS / ENG (확정 안 됨) | 같은 기체 관련 이력 없음 |
| S18 | 모호한 증상 | HL7713 | KAL / A321-211 | 기장 쪽 피토 히트 경고가 뜨고 좌우 속도계 값이 다르게 표시됨. | REVIEWING(SUPPLEMENT ×2) → HUMAN_REVIEW(RETRY_LIMIT) 또는 REVIEWING(HUMAN) → HUMAN_REVIEW(AGENT_NEEDS_HUMAN) | 후보 SYS / AVI (확정 안 됨) | 같은 기체 관련 이력 없음 |
| S19 | 모호한 증상 | HL7715 | JNA / A320-214 | 착륙 후 1번 브레이크 온도가 비정상적으로 높게 표시되는데 육안으로는 과열 흔적이 없음. | REVIEWING(SUPPLEMENT ×2) → HUMAN_REVIEW(RETRY_LIMIT) 또는 REVIEWING(HUMAN) → HUMAN_REVIEW(AGENT_NEEDS_HUMAN) | 후보 HYD / AVI (확정 안 됨) | 같은 기체 관련 이력 없음 |
| S20 | 모호한 증상 | HL7714 | AAR / A319-132 | L1 도어 슬라이드 압력 낮음 경고가 승무원 패널에 표시됨. 도어는 정상적으로 잠겨 있음. | REVIEWING(SUPPLEMENT ×2) → HUMAN_REVIEW(RETRY_LIMIT) 또는 REVIEWING(HUMAN) → HUMAN_REVIEW(AGENT_NEEDS_HUMAN) | 후보 CAB / STR (확정 안 됨) | 같은 기체 관련 이력 없음 |
| S21 | 같은 ATA·다른 JASC (LLM1 함정) | HL7705 | AAR / A320-214 | 착륙 후 BRAKES HOT 경고가 뜨고 2번 메인 휠 브레이크 온도가 다른 휠보다 높게 표시됨. | CLASSIFYING(sufficient=False, 연결 이력 없음) → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | HYD | MH-001305(ATA 32, JASC 3230)는 같은 ATA지만 다른 JASC라 연결하면 안 됨 (정답 JASC 3242) |
| S22 | 같은 ATA·다른 JASC (LLM1 함정) | HL7703 | JNA / A320-212 | 순항 중 ECAM AIR PACK 1 FAULT가 뜨고 팩 출구 온도가 높게 표시됨. | CLASSIFYING(sufficient=False, 연결 이력 없음) → INVESTIGATING → REVIEWING(PASS) → ASSIGNED | SYS | MH-001341(ATA 21, JASC 2160)는 같은 ATA지만 다른 JASC라 연결하면 안 됨 (정답 JASC 2150) |
| S23 | 반복 결함 (비행 조종) | HL7713 | KAL / A321-211 | 접근 중 플랩을 CONF 1로 내리자 ECAM F/CTL FLAP SYS 1 FAULT가 또 뜨고 플랩이 느리게 내려감. | CLASSIFYING → sufficient=True → 배정 실행 → ASSIGNED | FCS | MH-001567, MH-001574 (같은 기체 FLAP SYS 1 FAULT 반복, JASC 2750) |

## 2. 접수 단계 충돌 시나리오 (AI 서버까지 가지 않음, 422 NEEDS_SUPPLEMENT)

결과가 신고자 소속 항공사에 따라 달라집니다. 다른 항공사 기체는 다른 검사보다 먼저 FORBIDDEN_AIRLINE이 나오므로, 표의 신고자 계정(비밀번호 `test1234`)으로 보내세요.

| # | 신고자 계정 | 등록부호 | airline_code | aircraft_model | 입력할 증상 | 기대 응답 details |
|---|---|---|---|---|---|---|
| V1 | reporter1@kal.test | HL7999 | KAL | A320-214 | 순항 중 PACK 2 결함 메시지 | tail_number: NOT_FOUND |
| V2 | reporter1@kal.test | HL7701 | AAR | A320-232 | 객실 냄새 신고 | airline_code: MISMATCH (expected KAL) |
| V3 | reporter1@aar.test | HL7702 | AAR | A320-214 | 도어 경고 메시지 | aircraft_model: MISMATCH (expected A321-253N) |
| V4 | reporter1@aar.test | HL7720 | AAR | A320-271N | 착륙 후 브레이크 진동 | tail_number: INACTIVE |
| V5 | reporter1@jna.test | HL7703 | JNA | A320-212 | (빈 값) | symptom_text: REQUIRED |
| V6 | reporter1@aar.test | HL7701 | KAL | A320-232 | 객실 냄새 신고 | tail_number: FORBIDDEN_AIRLINE (다른 항공사 기체) |
