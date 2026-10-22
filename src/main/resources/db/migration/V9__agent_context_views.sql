-- business-mcp(B01·B02)가 읽는 AI용 뷰. backend 내부 테이블 대신 이 두 뷰가 계약입니다.
-- 1) agent_request_context: 접수 내용·상태·버전. V6의 repair_requests 열 단위 권한을 대체합니다.
--    내부 테이블 구조가 바뀌어도 이 뷰의 열만 유지하면 MCP 쪽은 그대로입니다.
CREATE VIEW public.agent_request_context AS
SELECT r.id AS request_id,
       r.tail_number,
       r.aircraft_model,
       r.airline_code,
       r.symptom_text,
       r.status,
       r.created_at,
       r.version
FROM public.repair_requests r;

-- 2) agent_run_context: 실행(run)마다 backend가 등록한 검색 범위. rag-mcp와 같은 행을 B01이 그대로 돌려줘
--    AI가 보는 범위(context_version·model_key·operator·고정 조건)와 실제 RAG 검색 범위가 하나가 됩니다.
CREATE VIEW public.agent_run_context AS
SELECT c.run_id,
       c.request_id,
       c.context_version,
       c.operator_code,
       c.model_key,
       c.locked_filters,
       c.active,
       c.expires_at
FROM public.rag_request_contexts c;

GRANT SELECT ON public.agent_request_context, public.agent_run_context TO mro_business_reader;
REVOKE SELECT (id, tail_number, aircraft_model, airline_code, symptom_text, status, created_at, version)
    ON public.repair_requests FROM mro_business_reader;
