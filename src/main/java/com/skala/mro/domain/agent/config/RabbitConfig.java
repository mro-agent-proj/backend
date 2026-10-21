package com.skala.mro.domain.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.mro.global.error.ApiException;
import java.util.Map;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * Spring ↔ AI 서버 메시징 토폴로지 (인터페이스 규약 1번 섹션). 큐·익스체인지 선언은 Spring이 소유합니다.
 *
 * <pre>
 * mro.agent (direct) ── run.requested ──▶ mro.agent.run     (Spring 발행 → AI 서버 소비, TTL = mro.ai.timeout)
 *                    └─ agent.event   ──▶ mro.agent.events  (AI 서버 발행 → Spring 소비)
 * mro.agent.dlx (direct) ── run.dead   ──▶ mro.agent.run.dlq    (Spring이 소비해 사람 검토로 전환)
 *                        └─ event.dead ──▶ mro.agent.events.dlq (보관. 관리 UI에서 확인·재전송)
 * </pre>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "mro.agent";
    public static final String DEAD_LETTER_EXCHANGE = "mro.agent.dlx";

    public static final String RUN_QUEUE = "mro.agent.run";
    public static final String RUN_ROUTING_KEY = "run.requested";
    public static final String RUN_DLQ = "mro.agent.run.dlq";
    public static final String RUN_DEAD_ROUTING_KEY = "run.dead";

    public static final String EVENT_QUEUE = "mro.agent.events";
    public static final String EVENT_ROUTING_KEY = "agent.event";
    public static final String EVENT_DLQ = "mro.agent.events.dlq";
    public static final String EVENT_DEAD_ROUTING_KEY = "event.dead";

    /** 일시적 오류(DB 등)는 이 횟수까지 재시도한 뒤 DLQ로 보냅니다. */
    public static final int MAX_ATTEMPTS = 3;

    @Bean
    Declarables agentTopology(AiProperties properties) {
        DirectExchange exchange = new DirectExchange(EXCHANGE);
        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE);
        Queue runQueue = QueueBuilder.durable(RUN_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RUN_DEAD_ROUTING_KEY)
                // AI 서버가 꺼져 있어 아무도 가져가지 않으면 만료되어 DLQ로 이동
                .ttl((int) properties.timeout().toMillis())
                .build();
        Queue eventQueue = QueueBuilder.durable(EVENT_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EVENT_DEAD_ROUTING_KEY)
                .build();
        Queue runDlq = QueueBuilder.durable(RUN_DLQ).build();
        Queue eventDlq = QueueBuilder.durable(EVENT_DLQ).build();
        return new Declarables(exchange, deadLetterExchange, runQueue, eventQueue, runDlq, eventDlq,
                BindingBuilder.bind(runQueue).to(exchange).with(RUN_ROUTING_KEY),
                BindingBuilder.bind(eventQueue).to(exchange).with(EVENT_ROUTING_KEY),
                BindingBuilder.bind(runDlq).to(deadLetterExchange).with(RUN_DEAD_ROUTING_KEY),
                BindingBuilder.bind(eventDlq).to(deadLetterExchange).with(EVENT_DEAD_ROUTING_KEY));
    }

    /** 본문은 JSON(snake_case). AI 서버는 content_type=application/json으로 발행해야 합니다. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * 재시도해도 결과가 같은 오류(형식 오류, 없는 요청 등 ApiException)는 바로 DLQ로 보내고,
     * 그 밖의 오류만 MAX_ATTEMPTS까지 재시도합니다. 재시도 간격은 spring.rabbitmq.listener.simple.retry.*.
     */
    @Bean
    RabbitRetryTemplateCustomizer listenerRetryPolicy() {
        return (target, template) -> {
            if (target == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                template.setRetryPolicy(new SimpleRetryPolicy(MAX_ATTEMPTS, Map.of(
                        ApiException.class, false,
                        MessageConversionException.class, false), true, true));
            }
        };
    }
}
