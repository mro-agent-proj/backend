-- business-mcp(B01·B02·B03)가 요청을 확인할 때 읽는 repair_requests 열만 mro_business_reader에 엽니다.
-- 팀 논의 C04, agent 저장소 docs/AI_SERVER_INTEGRATION.md "업무 조회 권한"의 열 목록과 같습니다.
-- 신고자 id·검토 사유·요약 등 나머지 열은 계속 막혀 있습니다.
GRANT SELECT (id, tail_number, aircraft_model, airline_code, symptom_text, status, created_at, version)
    ON public.repair_requests TO mro_business_reader;
