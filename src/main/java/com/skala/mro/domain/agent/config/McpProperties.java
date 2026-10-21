package com.skala.mro.domain.agent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 서버가 MCP를 부를 때 쓰는 요청별 위임 토큰과 RAG 검색 범위 설정.
 *
 * @param businessJwtSecret business-mcp가 검증하는 서명 비밀값 (agent 저장소 BUSINESS_JWT_SECRET과 같아야 함)
 * @param ragJwtSecret      rag-mcp가 검증하는 서명 비밀값 (agent 저장소 RAG_JWT_SECRET과 같아야 함)
 * @param tokenTtl          토큰 유효 시간. AI 처리 한도(mro.ai.timeout)보다 길어야 합니다
 * @param contextTtl        rag_request_contexts 행의 유효 시간
 * @param mappingRevision   검색 범위를 만든 규칙의 버전 (rag_request_contexts.mapping_revision)
 */
@ConfigurationProperties("mro.mcp")
public record McpProperties(String businessJwtSecret, String ragJwtSecret, Duration tokenTtl, Duration contextTtl,
                            String mappingRevision) {
}
