package com.skala.mro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Flyway V3(RAG 테이블)·V4(MCP 조회 전용 계정)가 의도한 범위로 적용됐는지 확인합니다. */
class DatabaseSchemaTest extends IntegrationTestSupport {

    private boolean canSelect(String role, String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select has_table_privilege(?, ?, 'SELECT')",
                Boolean.class, role, table));
    }

    private boolean canSelectColumn(String role, String table, String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select has_column_privilege(?, ?, ?, 'SELECT')",
                Boolean.class, role, table, column));
    }

    @Test
    void RAG_테이블과_pgvector가_생성된다() {
        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname = 'vector'", Integer.class))
                .isEqualTo(1);
        for (String table : new String[]{"public.knowledge_releases", "public.jasc_codes", "public.sdr_cases",
                "public.amm_references", "public.evidence", "public.rag_request_contexts",
                "public.tech_documents", "public.tech_chunks", "rag.tech_embeddings", "rag.sdr_embeddings"}) {
            assertThat(jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class, table))
                    .as(table).isTrue();
        }
    }

    @Test
    void AMM_의미검색_테이블은_V8에서_제거된다() {
        for (String table : new String[]{"public.amm_chunks", "public.amm_chunk_references", "rag.amm_embeddings"}) {
            assertThat(jdbc.queryForObject("select to_regclass(?) is null", Boolean.class, table)).as(table).isTrue();
        }
        assertThat(jdbc.queryForObject(
                "select format_type(atttypid, atttypmod) from pg_attribute where attrelid = 'rag.tech_embeddings'::regclass and attname = 'embedding'",
                String.class)).isEqualTo("vector(1024)");
    }

    @Test
    void rag_mcp_계정은_RAG_테이블만_읽는다() {
        assertThat(canSelect("mro_reader", "public.sdr_cases")).isTrue();
        assertThat(canSelect("mro_reader", "public.tech_chunks")).isTrue();
        assertThat(canSelect("mro_reader", "rag.tech_embeddings")).isTrue();
        assertThat(canSelect("mro_reader", "rag.sdr_embeddings")).isTrue();
        assertThat(canSelect("mro_reader", "public.users")).isFalse();
        assertThat(canSelect("mro_reader", "public.repair_requests")).isFalse();
        assertThat(canSelect("mro_reader", "public.maintenance_history")).isFalse();
    }

    @Test
    void business_mcp_계정은_기체_수리이력_부서만_읽는다() {
        assertThat(canSelect("mro_business_reader", "public.aircraft")).isTrue();
        assertThat(canSelect("mro_business_reader", "public.maintenance_history")).isTrue();
        assertThat(canSelect("mro_business_reader", "public.departments")).isTrue();
        assertThat(canSelect("mro_business_reader", "public.users")).isFalse();
        // 접수·실행 범위는 내부 테이블이 아니라 AI용 뷰로만 읽는다 (V9, V6 열 권한 대체)
        assertThat(canSelect("mro_business_reader", "public.agent_request_context")).isTrue();
        assertThat(canSelect("mro_business_reader", "public.agent_run_context")).isTrue();
        assertThat(canSelect("mro_business_reader", "public.repair_requests")).isFalse();
        assertThat(canSelect("mro_business_reader", "public.rag_request_contexts")).isFalse();
        for (String column : new String[]{"id", "tail_number", "status", "version", "reporter_id", "summary"}) {
            assertThat(canSelectColumn("mro_business_reader", "public.repair_requests", column)).as(column).isFalse();
        }
        assertThat(canSelect("mro_business_reader", "public.sdr_cases")).isFalse();
        assertThat(canSelect("mro_business_reader", "public.tech_chunks")).isFalse();
    }

    @Test
    void 조회_전용_계정은_쓰기_권한이_없다() {
        for (String role : new String[]{"mro_reader", "mro_business_reader"}) {
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.role_table_grants
                    where grantee = ? and privilege_type in ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE')""",
                    Integer.class, role)).as(role).isZero();
        }
    }
}
