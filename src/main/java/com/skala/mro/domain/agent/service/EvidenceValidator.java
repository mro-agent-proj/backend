package com.skala.mro.domain.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 흐름도 "배정 실행: 저장된 판단·근거·권한 검증"의 근거 부분.
 * AI가 COMPLETED(ASSIGN)에 담아 보낸 근거 ID가 실제로 있고 이 요청의 범위(정확한 모델)에 맞는지 공유 DB에서 확인합니다.
 *
 * <ul>
 *   <li>HISTORY {@code MH-*}: 수리이력에 있고, 그 기체가 요청과 같은 모델 (다른 기체 유사 사례 허용)</li>
 *   <li>SDR {@code EV-SDR-*} / AMM {@code EV-AMM-*}: READY release의 evidence에 있고, 종류가 맞고,
 *       원본 SDR의 model_key가 요청 모델과 같음</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EvidenceValidator {

    private static final String HISTORY_SQL = """
            select exists (
              select 1 from maintenance_history mh
              join aircraft a on a.tail_number = mh.tail_number
              where mh.id = ? and a.aircraft_model = ?)""";

    private static final String RAG_SQL = """
            select exists (
              select 1 from evidence e
              join knowledge_releases kr on kr.id = e.release_id and kr.state = 'READY'
              join sdr_cases s on s.release_id = e.release_id and s.id = e.sdr_case_uuid
              where e.evidence_id = ? and e.source_type = ? and s.model_key = ?)""";

    private final JdbcTemplate jdbc;

    /** 통과하지 못한 근거 목록. 비어 있으면 통과입니다. */
    public List<String> findProblems(String aircraftModel, JsonNode evidence) {
        if (evidence == null || !evidence.isArray() || evidence.isEmpty()) {
            return List.of("근거가 하나도 없습니다");
        }
        // SDR model_key는 하이픈 없는 정확 모델 (A320-232 → A320232)
        String modelKey = aircraftModel.replace("-", "");
        List<String> problems = new ArrayList<>();
        for (JsonNode item : evidence) {
            String source = item.path("source").asText("");
            String refId = item.path("ref_id").asText("");
            if (refId.isBlank()) {
                problems.add(source + " ref_id 없음");
                continue;
            }
            boolean valid = switch (source) {
                case "HISTORY" -> exists(HISTORY_SQL, refId, aircraftModel);
                case "SDR" -> exists(RAG_SQL, refId, "sdr", modelKey);
                case "AMM" -> exists(RAG_SQL, refId, "derived_amm_reference", modelKey);
                default -> false;
            };
            if (!valid) {
                problems.add(source.isBlank() ? refId : source + " " + refId);
            }
        }
        return problems;
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }
}
