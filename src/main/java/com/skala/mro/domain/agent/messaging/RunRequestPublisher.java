package com.skala.mro.domain.agent.messaging;

import com.skala.mro.domain.agent.config.RabbitConfig;
import com.skala.mro.domain.agent.dto.RunPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RunRequestPublisher {

    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbitTemplate;

    /** 브로커가 받았다고 확인(confirm)할 때까지 기다립니다. 실패하면 예외를 던집니다. */
    public void publish(RunPayload payload) {
        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RUN_ROUTING_KEY, payload, message -> {
                message.getMessageProperties().setMessageId(payload.requestId().toString());
                return message;
            });
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
