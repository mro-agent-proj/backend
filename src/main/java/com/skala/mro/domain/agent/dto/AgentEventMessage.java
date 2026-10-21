package com.skala.mro.domain.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.skala.mro.domain.agent.entity.AgentEventType;
import java.time.Instant;
import java.util.UUID;

/** mro.agent.events 큐 메시지 (AI 서버 → Spring). payload 형식은 type마다 다릅니다. */
public record AgentEventMessage(UUID eventId, UUID requestId, AgentEventType type, Instant occurredAt,
                                JsonNode payload) {
}
