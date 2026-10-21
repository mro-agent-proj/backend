package com.skala.mro.domain.agent.service;

import com.skala.mro.domain.agent.dto.RunPayload;
import com.skala.mro.domain.agent.dto.RunRequestedEvent;
import com.skala.mro.domain.agent.messaging.RunRequestPublisher;
import com.skala.mro.domain.request.repository.RepairRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 요청이 커밋된 뒤 MCP 위임(검색 범위·토큰)을 준비하고 실행 요청을 큐에 발행합니다.
 * 준비나 발행이 실패하면 사람 검토(SYSTEM_ERROR)로 넘깁니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDispatcher {

    private final RunRequestPublisher publisher;
    private final AgentEventService eventService;
    private final RepairRequestRepository requestRepository;
    private final McpDelegationService delegationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunRequested(RunRequestedEvent event) {
        try {
            requestRepository.findById(event.requestId())
                    .ifPresent(request -> publisher.publish(RunPayload.from(request), delegationService.prepare(request)));
        } catch (Exception e) {
            log.error("실행 요청 발행 실패: request_id={}", event.requestId(), e);
            eventService.failRun(event.requestId(), "실행 요청 발행 실패: " + e.getMessage());
        }
    }
}
