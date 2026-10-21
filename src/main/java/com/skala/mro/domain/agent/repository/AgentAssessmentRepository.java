package com.skala.mro.domain.agent.repository;

import com.skala.mro.domain.agent.entity.AgentAssessment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAssessmentRepository extends JpaRepository<AgentAssessment, Long> {

    List<AgentAssessment> findByRequestIdOrderByIdAsc(UUID requestId);
}
