package com.skala.mro.domain.request.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** stage: CLASSIFYING / INVESTIGATING / REVIEWING / FINAL. output은 AI 서버가 보낸 JSON 그대로. */
public record AssessmentItem(String stage, int attempt, JsonNode output, Instant createdAt) {
}
