package com.skala.mro.domain.agent.service;

import com.skala.mro.domain.agent.config.AiProperties;
import com.skala.mro.domain.request.entity.Actor;
import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.entity.RequestStatus;
import com.skala.mro.domain.request.entity.ReviewReason;
import com.skala.mro.domain.request.repository.RepairRequestRepository;
import com.skala.mro.domain.request.service.RequestWorkflow;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 접수 후 mro.ai.timeout 안에 끝나지 않은 요청을 사람 검토로 넘깁니다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTimeoutJob {

    private final RepairRequestRepository requestRepository;
    private final RequestWorkflow workflow;
    private final AiProperties properties;

    @Scheduled(fixedDelayString = "${mro.ai.timeout-check-interval-ms:30000}",
            initialDelayString = "${mro.ai.timeout-check-interval-ms:30000}")
    @Transactional
    public void expireStaleRuns() {
        Instant cutoff = Instant.now().minus(properties.timeout());
        List<RepairRequest> stale = requestRepository.findByStatusInAndCreatedAtBefore(RequestStatus.IN_PROGRESS,
                cutoff);
        for (RepairRequest request : stale) {
            log.warn("AI 처리 시간 초과: request_id={}, status={}", request.getId(), request.getStatus());
            workflow.sendToHumanReview(request, ReviewReason.SYSTEM_ERROR, Actor.SYSTEM,
                    "AI 응답 시간 초과 (%d분)".formatted(properties.timeout().toMinutes()));
        }
    }
}
