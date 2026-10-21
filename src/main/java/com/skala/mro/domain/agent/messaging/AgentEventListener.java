package com.skala.mro.domain.agent.messaging;

import com.skala.mro.domain.agent.config.RabbitConfig;
import com.skala.mro.domain.agent.dto.AgentEventMessage;
import com.skala.mro.domain.agent.service.AgentEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * AI 서버 이벤트 소비. 처리 실패 시:
 * 형식 오류·없는 요청(ApiException, 변환 실패) → 바로 mro.agent.events.dlq
 * 그 밖의 오류 → RabbitConfig.MAX_ATTEMPTS까지 재시도 후 DLQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEventListener {

    private final AgentEventService eventService;

    @RabbitListener(queues = RabbitConfig.EVENT_QUEUE)
    public void onEvent(AgentEventMessage event) {
        AgentEventService.Result result = eventService.handle(event);
        log.debug("AI 이벤트 처리: request_id={}, type={}, result={}", event.requestId(), event.type(), result);
    }
}
