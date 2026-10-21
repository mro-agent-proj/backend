package com.skala.mro.domain.agent.repository;

import com.skala.mro.domain.agent.entity.AgentEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEventRepository extends JpaRepository<AgentEvent, UUID> {
}
