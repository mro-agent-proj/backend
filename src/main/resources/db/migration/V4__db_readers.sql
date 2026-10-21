-- MCP 서버용 조회 전용 계정과 권한. 비밀번호는 마이그레이션에 넣지 않습니다.
-- 로컬은 docker/postgres/init 스크립트가 LOGIN PASSWORD로 먼저 만들고, 여기서는 계정이 없을 때만
-- 로그인할 수 없는(NOLOGIN) 계정으로 만들어 권한만 부여합니다. 운영에서는 ALTER ROLE로 비밀번호를 줍니다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mro_reader') THEN
        CREATE ROLE mro_reader NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mro_business_reader') THEN
        CREATE ROLE mro_business_reader NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO mro_reader, mro_business_reader', current_database());
END $$;

-- rag-mcp (세환): RAG 테이블만. 계정 이름은 rag-mcp 설정(RAG_DATABASE_URL)과 맞춥니다.
GRANT USAGE ON SCHEMA public, rag TO mro_reader;
GRANT SELECT ON public.knowledge_releases, public.knowledge_indexes,
    public.jasc_codes, public.sdr_cases, public.amm_references,
    public.amm_chunks, public.amm_chunk_references, public.evidence,
    public.rag_request_contexts, rag.amm_embeddings TO mro_reader;
ALTER ROLE mro_reader SET default_transaction_read_only = on;

-- business-mcp (나현): 기체·수리이력·부서 마스터만. 계정·요청 등 업무 테이블은 볼 수 없습니다.
GRANT USAGE ON SCHEMA public TO mro_business_reader;
GRANT SELECT ON public.airlines, public.aircraft, public.departments, public.maintenance_history
    TO mro_business_reader;
ALTER ROLE mro_business_reader SET default_transaction_read_only = on;
