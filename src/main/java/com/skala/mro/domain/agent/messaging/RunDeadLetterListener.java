package com.skala.mro.domain.agent.messaging;

import com.skala.mro.domain.agent.config.RabbitConfig;
import com.skala.mro.domain.agent.dto.RunPayload;
import com.skala.mro.domain.agent.service.AgentEventService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 실행 요청 DLQ 소비. AI 서버가 거절(reject)했거나 TTL 안에 아무도 가져가지 않은 요청을
 * 사람 검토(SYSTEM_ERROR)로 넘깁니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunDeadLetterListener {

    private final AgentEventService eventService;

    @RabbitListener(queues = RabbitConfig.RUN_DLQ)
    public void onDeadRun(RunPayload payload,
                          @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) {
        String reason = xDeath == null || xDeath.isEmpty() ? "unknown" : String.valueOf(xDeath.get(0).get("reason"));
        log.warn("실행 요청 DLQ 도착: request_id={}, reason={}", payload.requestId(), reason);
        if (payload.requestId() != null) {
            eventService.failRun(payload.requestId(), "AI 실행 요청이 DLQ로 이동 (" + describe(reason) + ")");
        }
    }

    private static String describe(String reason) {
        return switch (reason) {
            case "rejected" -> "AI 서버가 처리하지 못하고 거절";
            case "expired" -> "처리 시간 안에 AI 서버가 가져가지 않음";
            default -> reason;
        };
    }
}
