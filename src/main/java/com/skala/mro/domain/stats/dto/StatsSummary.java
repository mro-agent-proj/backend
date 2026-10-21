package com.skala.mro.domain.stats.dto;

import java.time.Instant;
import java.util.Map;

public record StatsSummary(Instant from, Instant to, long total, Map<String, Long> byStatus,
                           Map<String, Long> byDepartment, long autoAssigned, long humanReviewed,
                           Double autoAssignRate, Double avgMinutesToFirstAssignment) {
}
