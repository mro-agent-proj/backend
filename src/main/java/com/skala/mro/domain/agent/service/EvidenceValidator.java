package com.skala.mro.domain.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 흐름도 "배정 실행: 저장된 판단·근거·권한 검증"의 근거 부분.
 * AI가 COMPLETED(ASSIGN)에 담아 보낸 근거 ID가 실제로 있고 이 요청의 Backend 허용 모델·운항사·릴리스·고정 조건에 맞는지 공유 DB에서 확인합니다.
 *
 * <ul>
 *   <li>HISTORY {@code MH-*}: 수리이력에 있고, 그 기체가 요청과 같은 모델 (다른 기체 유사 사례 허용)</li>
 *   <li>SDR {@code EV-SDR-*}: READY release의 evidence에 있고, 종류가 맞고, 원본 SDR의 model_key가 실행 컨텍스트의 허용 목록에 있음</li>
 *   <li>AMM {@code EV-AMM-*}: 배정 근거로 인정하지 않음. AMM은 SDR 원문의 참조번호라 배정 후 참고 정보
 *       ({@code reference_amm})로만 전달합니다 (AI 서버 정책과 같음).</li>
 *   <li>DOC {@code EV-DOC-*}: R04 기술자료 조각(tech_chunks)에 있음. 특정 모델 자료가 아니므로 존재만 확인</li>
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
              where e.evidence_id = ? and e.source_type = ? and (
                exists (select 1 from rag_request_contexts c
                  where c.request_id = ? and c.active and c.expires_at > now()
                    and c.model_key = ? and c.release_id = e.release_id
                    and (c.operator_code is null or c.operator_code = s.operator_code)
                    and (s.model_key = c.model_key or
                         jsonb_exists(coalesce(c.model_scope->'levels'->'FAMILY', '[]'::jsonb), s.model_key))
                    and (coalesce(jsonb_array_length(c.locked_filters->'jasc_codes'), 0) = 0 or
                         jsonb_exists(c.locked_filters->'jasc_codes', s.reported_jasc))
                    and (c.locked_filters->>'difficulty_from' is null or
                         s.difficulty_date >= (c.locked_filters->>'difficulty_from')::date)
                    and (c.locked_filters->>'difficulty_to' is null or
                         s.difficulty_date <= (c.locked_filters->>'difficulty_to')::date))
                or (s.model_key = ? and not exists (
                    select 1 from rag_request_contexts c where c.request_id = ?))
              ))""";

    private static final String DOC_SQL = "select exists (select 1 from tech_chunks where evidence_id = ?)";

    private final JdbcTemplate jdbc;

    /** 통과하지 못한 근거 목록. 비어 있으면 통과입니다. */
    public List<String> findProblems(UUID requestId, String aircraftModel, JsonNode evidence) {
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
            if ("AMM".equals(source)) {
                problems.add("AMM " + refId + " (AMM은 참고 정보 reference_amm이며 배정 근거가 아님)");
                continue;
            }
            boolean valid = switch (source) {
                case "HISTORY" -> exists(HISTORY_SQL, refId, aircraftModel);
                case "SDR" -> exists(RAG_SQL, refId, "sdr", requestId.toString(), modelKey, modelKey, requestId.toString());
                case "DOC" -> exists(DOC_SQL, refId);
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
