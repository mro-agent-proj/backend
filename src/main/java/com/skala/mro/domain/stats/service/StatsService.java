package com.skala.mro.domain.stats.service;

import com.skala.mro.domain.stats.dto.StatsSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 어드민 대시보드 요약. 기간은 요청 접수 시각(created_at) 기준입니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private static final String IN_RANGE = "r.created_at >= :from and r.created_at < :to";

    private final NamedParameterJdbcTemplate jdbc;

    public StatsSummary summary(Instant from, Instant to) {
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now().plus(1, ChronoUnit.DAYS);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(start))
                .addValue("to", Timestamp.from(end));

        Map<String, Long> byStatus = countBy("""
                select r.status as k, count(*) as c from repair_requests r
                where %s group by r.status order by r.status""".formatted(IN_RANGE), params);
        Map<String, Long> byDepartment = countBy("""
                select a.department_code as k, count(*) as c from assignments a
                join repair_requests r on r.id = a.request_id
                where %s group by a.department_code order by a.department_code""".formatted(IN_RANGE), params);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long autoAssigned = count("""
                select count(distinct a.request_id) from assignments a
                join repair_requests r on r.id = a.request_id
                where a.assigned_by = 'AI' and %s""".formatted(IN_RANGE), params);
        long humanReviewed = count("""
                select count(distinct h.request_id) from human_reviews h
                join repair_requests r on r.id = h.request_id
                where %s""".formatted(IN_RANGE), params);
        Double avgMinutes = jdbc.queryForObject("""
                select avg(extract(epoch from (fa.first_at - r.created_at))) / 60
                from repair_requests r
                join (select request_id, min(created_at) as first_at from assignments group by request_id) fa
                  on fa.request_id = r.id
                where %s""".formatted(IN_RANGE), params, Double.class);

        return new StatsSummary(start, end, total, byStatus, byDepartment, autoAssigned, humanReviewed,
                total == 0 ? null : (double) autoAssigned / total, avgMinutes);
    }

    private Map<String, Long> countBy(String sql, MapSqlParameterSource params) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            result.put(rs.getString("k"), rs.getLong("c"));
        });
        return result;
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }
}
