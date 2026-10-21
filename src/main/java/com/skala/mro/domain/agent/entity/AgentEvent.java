package com.skala.mro.domain.agent.entity;

import com.skala.mro.domain.agent.dto.AgentEventMessage;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 받은 AI 이벤트 원본. event_id로 중복 수신을 걸러냅니다. */
@Entity
@Table(name = "agent_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentEvent {

    @Id
    private UUID eventId;

    private UUID requestId;

    @Enumerated(EnumType.STRING)
    private AgentEventType type;

    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private Instant receivedAt;

    public AgentEvent(AgentEventMessage event, String payloadJson) {
        this.eventId = event.eventId();
        this.requestId = event.requestId();
        this.type = event.type();
        this.occurredAt = event.occurredAt();
        this.payload = payloadJson;
        this.receivedAt = Instant.now();
    }
}
