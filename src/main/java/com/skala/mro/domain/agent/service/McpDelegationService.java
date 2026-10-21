package com.skala.mro.domain.agent.service;

import com.skala.mro.domain.agent.config.McpProperties;
import com.skala.mro.domain.request.entity.RepairRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실행 요청마다 AI 서버가 MCP를 부를 수 있게 위임을 준비합니다 (팀 논의 C01).
 * <ol>
 *   <li>rag_request_contexts에 이 실행(run)의 검색 범위를 등록합니다. rag-mcp는 토큰의 run_id로 이 행을 찾아 범위를 고정합니다.</li>
 *   <li>business-mcp·rag-mcp용 토큰을 각각 서명해 RabbitMQ 헤더로 넘깁니다 (AI 서버 MCP_DELEGATION_TRANSPORT=amqp_headers).</li>
 * </ol>
 * 검색 범위는 시연 정책(C02)에 따라 항공사(운항사) 없이 기종만 고정합니다. 우리 항공사 코드는 SDR 운항사와 대응되지 않습니다.
 */
@Slf4j
@Service
public class McpDelegationService {

    public static final String BUSINESS_TOKEN_HEADER = "x-mro-business-token";
    public static final String RAG_TOKEN_HEADER = "x-mro-rag-token";
    /** 요청의 검색 조건(기체·기종)은 바뀌지 않으므로 항상 1입니다. business-mcp B01도 1을 돌려줍니다. */
    public static final int CONTEXT_VERSION = 1;
    static final String ISSUER = "mro-backend";
    static final String BUSINESS_AUDIENCE = "mro-business";
    static final String RAG_AUDIENCE = "mro-rag";
    private static final String NO_LOCKED_FILTERS = "{\"jasc_codes\": [], \"difficulty_from\": null, \"difficulty_to\": null}";

    private final JdbcTemplate jdbc;
    private final McpProperties properties;
    private final SecretKey businessKey;
    private final SecretKey ragKey;

    public McpDelegationService(JdbcTemplate jdbc, McpProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.businessKey = key("mro.mcp.business-jwt-secret", properties.businessJwtSecret());
        this.ragKey = key("mro.mcp.rag-jwt-secret", properties.ragJwtSecret());
        if (properties.businessJwtSecret().equals(properties.ragJwtSecret())) {
            throw new IllegalStateException("mro.mcp의 business·rag 서명 비밀값은 서로 달라야 합니다.");
        }
    }

    /** 검색 범위를 등록하고, RabbitMQ 실행 요청에 붙일 헤더(토큰 2개)를 돌려줍니다. */
    @Transactional
    public Map<String, Object> prepare(RepairRequest request) {
        String runId = UUID.randomUUID().toString();
        publishRagContext(request, runId);
        return Map.of(
                BUSINESS_TOKEN_HEADER, sign(businessKey, BUSINESS_AUDIENCE, request, runId),
                RAG_TOKEN_HEADER, sign(ragKey, RAG_AUDIENCE, request, runId));
    }

    private void publishRagContext(RepairRequest request, String runId) {
        String requestId = request.getId().toString();
        String tenantId = request.getAirlineCode();
        List<UUID> releases = jdbc.queryForList(
                "select id from knowledge_releases where state = 'READY' order by created_at desc limit 1", UUID.class);
        if (releases.isEmpty()) {
            log.warn("READY 상태의 RAG 릴리스가 없어 검색 범위를 등록하지 않습니다 (RAG 데이터 복원 전). request_id={}", requestId);
            return;
        }
        UUID releaseId = releases.get(0);
        // rag-mcp는 (dataset, method)마다 색인이 하나여야 합니다.
        Integer ambiguous = jdbc.queryForObject("""
                select count(*) from (select 1 from knowledge_indexes where release_id = ? and state = 'READY'
                                      group by dataset, method having count(*) > 1) d""", Integer.class, releaseId);
        if (ambiguous != null && ambiguous > 0) {
            log.warn("RAG 색인이 모호해 검색 범위를 등록하지 않습니다. release_id={}", releaseId);
            return;
        }
        // 같은 요청의 범위 등록이 동시에 일어나지 않게 막습니다 (agent 저장소 publish_context와 같은 잠금 키).
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class,
                "context:" + tenantId + ":" + requestId);
        jdbc.update("update rag_request_contexts set active = false where request_id = ? and tenant_id = ? and context_version <> ?",
                requestId, tenantId, CONTEXT_VERSION);
        jdbc.update("""
                        insert into rag_request_contexts (run_id, request_id, context_version, subject_id, tenant_id, operator_code,
                          model_key, release_id, index_ids, mapping_revision, locked_filters, active, expires_at)
                        values (?, ?, ?, ?, ?, null, ?, ?,
                          (select coalesce(jsonb_agg(index_key order by index_key), '[]'::jsonb) from knowledge_indexes
                           where release_id = ? and state = 'READY'),
                          ?, ?::jsonb, true, ?)""",
                runId, requestId, CONTEXT_VERSION, subjectId(request), tenantId, modelKey(request.getAircraftModel()),
                releaseId, releaseId, properties.mappingRevision(), NO_LOCKED_FILTERS,
                Timestamp.from(Instant.now().plus(properties.contextTtl())));
    }

    private String sign(SecretKey key, String audience, RepairRequest request, String runId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subjectId(request))
                .claim("tenant_id", request.getAirlineCode())
                .claim("run_id", runId)
                .claim("request_id", request.getId().toString())
                .claim("context_version", CONTEXT_VERSION)
                .issuer(ISSUER)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.tokenTtl())))
                // 키가 64바이트 이상이어도 MCP 쪽(PyJWT, HS256만 허용)과 맞게 고정합니다.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private static String subjectId(RepairRequest request) {
        return request.getReporterId().toString();
    }

    /** SDR model_key는 하이픈 없는 정확 모델입니다 (A320-232 → A320232). EvidenceValidator와 같은 규칙. */
    static String modelKey(String aircraftModel) {
        return aircraftModel.replace("-", "");
    }

    private static SecretKey key(String name, String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(name + "은 32바이트 이상이어야 합니다.");
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
