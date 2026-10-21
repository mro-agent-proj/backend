package com.skala.mro.domain.agent.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** LLM 단계별 판단·근거. 판단 근거 조회 API(/requests/{id}/trace)로 보여줍니다. */
@Entity
@Table(name = "agent_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentAssessment {

    public static final String FINAL_STAGE = "FINAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID requestId;

    private String stage;

    private int attempt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String output;

    private Instant createdAt;

    public AgentAssessment(UUID requestId, String stage, int attempt, String output) {
        this.requestId = requestId;
        this.stage = stage;
        this.attempt = attempt;
        this.output = output;
        this.createdAt = Instant.now();
    }
}
