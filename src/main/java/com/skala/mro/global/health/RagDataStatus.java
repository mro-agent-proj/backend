package com.skala.mro.global.health;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** RAG 데이터(세환님 전달본)와 R04 기술자료(경님 전달본)가 복원됐는지 확인합니다. */
@Component
@RequiredArgsConstructor
public class RagDataStatus {

    private final JdbcTemplate jdbc;

    /**
     * @param releaseKey READY release 키. 없으면 null
     * @param readyIndexes READY 색인 수
     * @param sdrCases SDR 사례 수
     * @param techDocuments R04 기술자료 문서 수 (경님 전달본)
     */
    public record Snapshot(String releaseKey, int readyIndexes, long sdrCases, long techDocuments) {

        public boolean ready() {
            return releaseKey != null && readyIndexes > 0 && sdrCases > 0;
        }
    }

    public Snapshot check() {
        return jdbc.queryForObject("""
                select (select max(release_key) from knowledge_releases where state = 'READY') as release_key,
                       (select count(*) from knowledge_indexes where state = 'READY') as ready_indexes,
                       (select count(*) from sdr_cases) as sdr_cases,
                       (select count(*) from tech_documents) as tech_documents""",
                (rs, i) -> new Snapshot(rs.getString("release_key"), rs.getInt("ready_indexes"),
                        rs.getLong("sdr_cases"), rs.getLong("tech_documents")));
    }
}
