package com.skala.mro;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 배정 실행 직전 근거 ID 검증 (MH-*: 수리이력, EV-SDR: RAG evidence, EV-DOC: R04 기술자료, EV-AMM: 근거 아님). */
class EvidenceValidationTest extends IntegrationTestSupport {

    private static final UUID RELEASE = UUID.fromString("00000000-0000-0000-0000-00000000aa01");
    private static final UUID SDR_A320_214 = UUID.fromString("00000000-0000-0000-0000-00000000bb01");
    private static final UUID SDR_A321 = UUID.fromString("00000000-0000-0000-0000-00000000bb02");
    private static final UUID AMM_REF = UUID.fromString("00000000-0000-0000-0000-00000000cc01");

    @BeforeEach
    void ragFixture() {
        jdbc.update("""
                insert into knowledge_releases (id, release_key, source_snapshot_id, package_version, manifest_sha256,
                  transform_version, codebook_snapshot_id, source_manifest, package_manifest, state, created_at)
                values (?, 'REL-TEST', 's', 'p', 'm', 't', 'c', '{}', '{}', 'READY', now())""", RELEASE);
        jdbc.update("""
                insert into jasc_codes (id, release_id, code, title, title_status, codebook_version, source_url,
                  title_source_file, source_payload)
                values (gen_random_uuid(), ?, '3230', 'LANDING GEAR', 'ok', 'v', 'u', 'f', '{}')""", RELEASE);
        sdrCase(SDR_A320_214, "CASE-1", "A320214");
        sdrCase(SDR_A321, "CASE-2", "A321272N");
        jdbc.update("""
                insert into amm_references (id, release_id, distilled_record_id, sdr_case_uuid, source_version,
                  cited_amm_ref_raw, cited_amm_ref_normalized, reference_level, reference_start, reference_end, quote,
                  quote_start, quote_end, relationship_status, extraction_status, semantic_review_status,
                  applicability_status, extraction_version, source_payload)
                values (?, ?, 'DR-1', ?, 'v', '32-11-00', '32-11-00', 'chapter_section', 5, 13, 'PER AMM 32-11-00',
                  0, 16, 'explicit', 'ok', 'not_reviewed', 'not_verified', 'v', '{}')""",
                AMM_REF, RELEASE, SDR_A320_214);
        evidence("EV-SDR-TEST1", "sdr", SDR_A320_214, null);
        evidence("EV-SDR-OTHER-MODEL", "sdr", SDR_A321, null);
        evidence("EV-AMM-TEST1", "derived_amm_reference", SDR_A320_214, AMM_REF);
        jdbc.update("""
                insert into tech_documents (document_id, title, url, published, category, ata_chapters, departments,
                  applicability_status, configuration_conditions, source_version, pdf_sha256, body_text)
                values ('DOC-test', 'Dual hydraulic loss', 'https://example.com/a.pdf', 'June 2007', 'maintenance',
                  '{29}', '{HYD}', 'supported', '{}', 'tech-v1', 'sha', 'HYDRAULIC LEAK CHECK')""");
        jdbc.update("""
                insert into tech_chunks (chunk_id, document_id, evidence_id, evidence_group_id, section, text,
                  start_offset, end_offset, applicability_status, configuration_conditions, embedding_text, content_hash)
                values ('DOC-test-01', 'DOC-test', 'EV-DOC-0123456789abcdef0123', 'DOC-test', 'Intro',
                  'HYDRAULIC LEAK', 0, 14, 'supported', '{}', 'Dual hydraulic loss > Intro\nHYDRAULIC LEAK', 'h')""");
    }

    private void sdrCase(UUID id, String caseId, String modelKey) {
        jdbc.update("""
                insert into sdr_cases (id, release_id, case_id, operator_code, make_raw, model_raw, model_key,
                  difficulty_date, submission_date, submission_at, submission_date_raw, reported_jasc, narrative_raw,
                  part, component, reported_action_candidates, reported_outcome_candidates, evidence_group_id,
                  split_group_id, split, search_eligible, source_version, source_file, source_record_no,
                  raw_record_sha256, raw_record, source_payload)
                values (?, ?, ?, 'AALA', 'AIRBUS', ?, ?, '2025-01-01', '2025-01-02', now(), '2025-01-02', '3230',
                  'LEFT MLG HYD LEAK', '{}', '{}', '[]', '[]', ?, 'g', 'corpus', true, 'v', 'f', 1, 'h', '{}', '{}')""",
                id, RELEASE, caseId, modelKey, modelKey, "EG-" + caseId);
    }

    private void evidence(String evidenceId, String type, UUID sdrCase, UUID ammRef) {
        jdbc.update("""
                insert into evidence (id, release_id, evidence_id, source_type, sdr_case_uuid, amm_reference_uuid,
                  source_id, source_version, locator, quote)
                values (gen_random_uuid(), ?, ?, ?, ?, ?, 's', 'v', '{}', 'q')""",
                RELEASE, evidenceId, type, sdrCase, ammRef);
    }

    private UUID completeWith(List<Map<String, String>> evidence) throws Exception {
        UUID requestId = submitRequest(login("reporter@kal.test"), "좌측 메인기어 유압유 누유");
        sendEvent(requestId, "COMPLETED", Map.of("outcome", "ASSIGN", "department", "HYD", "evidence", evidence));
        return requestId;
    }

    private String reviewNote(UUID requestId) {
        return jdbc.queryForObject("select note from human_reviews where request_id = ?", String.class, requestId);
    }

    @Test
    void 같은_모델의_수리이력_SDR_기술자료_근거면_배정된다() throws Exception {
        UUID requestId = completeWith(List.of(
                Map.of("source", "HISTORY", "ref_id", "MH-000001"),
                Map.of("source", "SDR", "ref_id", "EV-SDR-TEST1"),
                Map.of("source", "DOC", "ref_id", "EV-DOC-0123456789abcdef0123")));
        assertThat(requestStatus(requestId)).isEqualTo("ASSIGNED");
    }

    @Test
    void AMM은_존재해도_배정_근거로_인정하지_않는다() throws Exception {
        // AMM은 배정 후 참고 정보(reference_amm)다. AMM만 근거로 온 결과는 다른 생산자가 보냈더라도 배정하지 않는다.
        UUID requestId = completeWith(List.of(Map.of("source", "AMM", "ref_id", "EV-AMM-TEST1")));
        assertThat(requestStatus(requestId)).isEqualTo("HUMAN_REVIEW");
        assertThat(reviewNote(requestId)).contains("AMM EV-AMM-TEST1", "참고 정보");
    }

    @Test
    void 근거가_없으면_배정하지_않는다() throws Exception {
        UUID requestId = completeWith(List.of());
        assertThat(requestStatus(requestId)).isEqualTo("HUMAN_REVIEW");
        assertThat(reviewNote(requestId)).contains("근거가 하나도 없습니다");
    }

    @Test
    void 없는_ID_다른_모델_종류가_틀린_근거는_모두_찾아서_사람검토로_보낸다() throws Exception {
        UUID requestId = completeWith(List.of(
                Map.of("source", "HISTORY", "ref_id", "MH-000001"),
                Map.of("source", "HISTORY", "ref_id", "MH-999999"),
                Map.of("source", "HISTORY", "ref_id", "MH-000002"),
                Map.of("source", "SDR", "ref_id", "EV-SDR-OTHER-MODEL"),
                Map.of("source", "AMM", "ref_id", "EV-SDR-TEST1"),
                Map.of("source", "DOC", "ref_id", "EV-DOC-ffffffffffffffffffff"),
                Map.of("source", "MANUAL", "ref_id", "X-1")));

        assertThat(requestStatus(requestId)).isEqualTo("HUMAN_REVIEW");
        assertThat(jdbc.queryForObject("select review_reason from repair_requests where id = ?", String.class,
                requestId)).isEqualTo("VALIDATION_FAILED");
        assertThat(reviewNote(requestId))
                .contains("HISTORY MH-999999", "HISTORY MH-000002", "SDR EV-SDR-OTHER-MODEL", "AMM EV-SDR-TEST1",
                        "DOC EV-DOC-ffffffffffffffffffff", "MANUAL X-1")
                .doesNotContain("MH-000001,");
        assertThat(jdbc.queryForObject("select count(*) from assignments", Integer.class)).isZero();
    }

    @Test
    void READY가_아닌_release의_근거는_인정하지_않는다() throws Exception {
        jdbc.update("update knowledge_releases set state = 'RETIRED' where id = ?", RELEASE);
        UUID requestId = completeWith(List.of(Map.of("source", "SDR", "ref_id", "EV-SDR-TEST1")));
        assertThat(requestStatus(requestId)).isEqualTo("HUMAN_REVIEW");
    }
}
