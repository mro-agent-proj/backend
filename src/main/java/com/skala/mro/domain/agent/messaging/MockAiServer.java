package com.skala.mro.domain.agent.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skala.mro.domain.agent.config.AiProperties;
import com.skala.mro.domain.agent.config.RabbitConfig;
import com.skala.mro.domain.agent.dto.AgentEventMessage;
import com.skala.mro.domain.agent.dto.RunPayload;
import com.skala.mro.domain.agent.entity.AgentEventType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 서버 없이 개발하기 위한 가짜 AI 서버 (mro.ai.mode=mock).
 * 실제 AI 서버와 같은 큐·메시지 형식을 씁니다. 증상 키워드로 부서를 고르고, 같은 기체의 최근 수리이력을 근거로 보냅니다.
 * 부서를 못 고르거나 이력이 없으면 사람 검토로 보냅니다.
 * 실제 AI 서버와 동시에 켜면 실행 요청을 나눠 가져가므로 함께 쓰지 마세요.
 */
@Component
@ConditionalOnProperty(name = "mro.ai.mode", havingValue = "mock")
@RequiredArgsConstructor
public class MockAiServer {

    private static final Map<String, String[]> KEYWORDS = new LinkedHashMap<>();

    static {
        // 부서 코드 → {대표 ATA, 키워드...}
        KEYWORDS.put("HYD", new String[]{"32", "유압", "기어", "랜딩", "브레이크", "타이어"});
        KEYWORDS.put("ENG", new String[]{"72", "엔진", "추력", "EGT", "오일", "APU"});
        KEYWORDS.put("AVI", new String[]{"34", "계기", "통신", "항법", "오토파일럿", "레이더", "FMS"});
        KEYWORDS.put("ELE", new String[]{"24", "전기", "배터리", "발전기", "조명"});
        KEYWORDS.put("CAB", new String[]{"25", "객실", "좌석", "화장실", "갤리"});
        KEYWORDS.put("STR", new String[]{"53", "균열", "부식", "동체", "도어"});
        KEYWORDS.put("SYS", new String[]{"21", "공조", "여압", "연료", "산소"});
    }

    private final RabbitTemplate rabbitTemplate;
    private final JdbcTemplate jdbc;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.RUN_QUEUE)
    public void onRun(RunPayload run) {
        UUID requestId = run.requestId();
        Map.Entry<String, String[]> match = KEYWORDS.entrySet().stream()
                .filter(e -> containsAny(run.symptomText(), e.getValue()))
                .findFirst().orElse(null);

        pause();
        send(requestId, AgentEventType.STAGE_STARTED, node().put("stage", "CLASSIFYING").put("attempt", 1));

        pause();
        ObjectNode classification = node()
                .put("ata_chapter", match == null ? "05" : match.getValue()[0])
                .put("sufficient", match != null)
                .put("proposed_department", match == null ? null : match.getKey())
                .put("rationale", "[MOCK] 증상 키워드 기반 분류");
        classification.putArray("linked_history_ids");
        ObjectNode result = node().put("stage", "CLASSIFYING").put("attempt", 1);
        result.set("output", classification);
        send(requestId, AgentEventType.STAGE_RESULT, result);

        pause();
        // 근거: 같은 기체의 가장 최근 수리이력 (배정 실행 때 Spring이 근거 검증을 하므로 실제 ID를 보냄)
        String historyId = jdbc.query(
                "select id from maintenance_history where tail_number = ? order by reported_at desc limit 1",
                rs -> rs.next() ? rs.getString(1) : null, run.tailNumber());
        if (match != null && historyId != null) {
            ObjectNode completed = node().put("outcome", "ASSIGN").put("department", match.getKey())
                    .put("summary", "[MOCK] 증상 키워드로 " + match.getKey() + " 배정");
            completed.putArray("evidence").addObject().put("source", "HISTORY").put("ref_id", historyId)
                    .put("excerpt", "[MOCK] 같은 기체의 최근 수리이력").put("why", "[MOCK]");
            send(requestId, AgentEventType.COMPLETED, completed);
        } else {
            send(requestId, AgentEventType.COMPLETED, node().put("outcome", "HUMAN_REVIEW")
                    .put("review_reason", "AGENT_NEEDS_HUMAN")
                    .put("summary", "[MOCK] 증상만으로 담당 부서를 정할 수 없습니다."));
        }
    }

    private void send(UUID requestId, AgentEventType type, JsonNode payload) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.EVENT_ROUTING_KEY,
                new AgentEventMessage(UUID.randomUUID(), requestId, type, Instant.now(), payload));
    }

    private ObjectNode node() {
        return objectMapper.createObjectNode();
    }

    private void pause() {
        try {
            Thread.sleep(properties.mockDelay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean containsAny(String text, String[] values) {
        for (int i = 1; i < values.length; i++) {
            if (text.toUpperCase().contains(values[i].toUpperCase())) {
                return true;
            }
        }
        return false;
    }
}
