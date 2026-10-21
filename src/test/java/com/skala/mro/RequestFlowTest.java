package com.skala.mro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.GetResponse;
import com.skala.mro.domain.agent.config.RabbitConfig;
import com.skala.mro.domain.agent.service.AgentTimeoutJob;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RequestFlowTest extends IntegrationTestSupport {

    @Autowired
    private AgentTimeoutJob timeoutJob;

    private ResultActions create(String token, Map<String, String> body) throws Exception {
        return mvc.perform(as(token, post("/api/v1/requests")).contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void 접수_확인_누락과_충돌은_422_보완안내이고_요청을_만들지_않는다() throws Exception {
        String reporter = login("reporter@kal.test");

        create(reporter, Map.of("tail_number", "HL7701", "airline_code", "KAL", "aircraft_model", "A320-214",
                "symptom_text", " "))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NEEDS_SUPPLEMENT"))
                .andExpect(jsonPath("$.error.details[0].field").value("symptom_text"))
                .andExpect(jsonPath("$.error.details[0].issue").value("REQUIRED"));

        create(reporter, Map.of("tail_number", "HL9999", "airline_code", "KAL", "aircraft_model", "A320-214",
                "symptom_text", "누유"))
                .andExpect(jsonPath("$.error.details[0].issue").value("NOT_FOUND"));

        create(reporter, Map.of("tail_number", "hl-7701", "airline_code", "AAR", "aircraft_model", "A321-231",
                "symptom_text", "누유"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].field").value("airline_code"))
                .andExpect(jsonPath("$.error.details[0].issue").value("MISMATCH"))
                .andExpect(jsonPath("$.error.details[0].expected").value("KAL"))
                .andExpect(jsonPath("$.error.details[1].field").value("aircraft_model"))
                .andExpect(jsonPath("$.error.details[1].expected").value("A320-214"));

        create(reporter, Map.of("tail_number", "HL7703", "airline_code", "KAL", "aircraft_model", "A320-214",
                "symptom_text", "누유"))
                .andExpect(jsonPath("$.error.details[0].issue").value("INACTIVE"));

        // 다른 항공사 기체: 상세 정보 없이 FORBIDDEN_AIRLINE만
        create(reporter, Map.of("tail_number", "HL8001", "airline_code", "KAL", "aircraft_model", "A320-214",
                "symptom_text", "누유"))
                .andExpect(jsonPath("$.error.details[0].issue").value("FORBIDDEN_AIRLINE"))
                .andExpect(jsonPath("$.error.details.length()").value(1));

        assertThat(count("select count(*) from repair_requests")).isZero();
        assertThat(rabbitTemplate.receive(RabbitConfig.RUN_QUEUE, 500)).isNull();
    }

    @Test
    void 접수하면_202_QUEUED이고_커밋후_실행요청을_큐에_발행한다() throws Exception {
        UUID requestId = submitRequest(login("reporter@kal.test"), "좌측 메인기어 유압유 누유");
        assertThat(requestStatus(requestId)).isEqualTo("QUEUED");

        Message message = rabbitTemplate.receive(RabbitConfig.RUN_QUEUE, WAIT.toMillis());
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(requestId.toString());
        JsonNode run = objectMapper.readTree(message.getBody());
        assertThat(run.get("request_id").asText()).isEqualTo(requestId.toString());
        assertThat(run.get("tail_number").asText()).isEqualTo("HL7701");
        assertThat(run.get("aircraft_model").asText()).isEqualTo("A320-214");
        assertThat(run.get("symptom_text").asText()).isEqualTo("좌측 메인기어 유압유 누유");
    }

    @Test
    void 재조사_루프를_거쳐_배정되고_정비팀이_수락한다() throws Exception {
        String reporter = login("reporter@kal.test");
        UUID requestId = submitRequest(reporter, "좌측 메인기어 유압유 누유");

        sendEvent(requestId, "STAGE_STARTED", Map.of("stage", "CLASSIFYING", "attempt", 1));
        sendEvent(requestId, "STAGE_RESULT", Map.of("stage", "CLASSIFYING", "attempt", 1,
                "output", Map.of("ata_chapter", "32", "sufficient", false, "linked_history_ids", List.of())));
        sendEvent(requestId, "STAGE_STARTED", Map.of("stage", "INVESTIGATING", "attempt", 1));
        sendEvent(requestId, "STAGE_STARTED", Map.of("stage", "REVIEWING", "attempt", 1));
        sendEvent(requestId, "STAGE_RESULT", Map.of("stage", "REVIEWING", "attempt", 1,
                "output", Map.of("decision", "SUPPLEMENT", "supplement_request", "SDR 추가 확인")));
        sendEvent(requestId, "STAGE_STARTED", Map.of("stage", "INVESTIGATING", "attempt", 2));
        assertThat(requestStatus(requestId)).isEqualTo("INVESTIGATING");
        sendEvent(requestId, "STAGE_STARTED", Map.of("stage", "REVIEWING", "attempt", 2));

        UUID completedId = UUID.randomUUID();
        Map<String, Object> completed = Map.of("outcome", "ASSIGN", "department", "HYD",
                "summary", "좌측 MLG 유압 호스 반복 누유 의심",
                "evidence", List.of(Map.of("source", "HISTORY", "ref_id", "MH-000001")));
        publishEvent(requestId, completedId, "COMPLETED", completed);
        // 같은 event_id 재전송은 무시. 뒤따르는 이벤트가 처리될 때까지 기다려 순서를 보장합니다.
        publishEvent(requestId, completedId, "COMPLETED", completed);
        sendEvent(requestId, "STAGE_RESULT", Map.of("stage", "REVIEWING"));
        assertThat(count("select count(*) from assignments where request_id = ?", requestId)).isEqualTo(1);

        mvc.perform(as(reporter, get("/api/v1/requests/{id}", requestId)))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assigned_department").value("HYD"))
                .andExpect(jsonPath("$.summary").value("좌측 MLG 유압 호스 반복 누유 의심"));

        String hyd = login("hyd@mro.test");
        JsonNode inbox = body(mvc.perform(as(hyd, get("/api/v1/assignments").param("status", "PENDING")))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].assigned_by").value("AI"))
                .andExpect(jsonPath("$.items[0].request.tail_number").value("HL7701")));
        String assignmentId = inbox.get("items").get(0).get("id").asText();

        // 다른 부서는 남의 배정을 수락할 수 없음
        mvc.perform(as(login("eng@mro.test"), post("/api/v1/assignments/{id}/accept", assignmentId)))
                .andExpect(status().isNotFound());

        mvc.perform(as(hyd, post("/api/v1/assignments/{id}/accept", assignmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        assertThat(requestStatus(requestId)).isEqualTo("ACCEPTED");
        mvc.perform(as(hyd, post("/api/v1/assignments/{id}/accept", assignmentId)))
                .andExpect(status().isConflict());

        JsonNode trace = body(mvc.perform(as(hyd, get("/api/v1/requests/{id}/trace", requestId)))
                .andExpect(status().isOk()));
        assertThat(trace.get("assessments")).hasSize(3); // CLASSIFYING, REVIEWING, FINAL
        assertThat(trace.get("assessments").get(2).get("output").get("evidence").get(0).get("ref_id").asText())
                .isEqualTo("MH-000001");
        assertThat(trace.get("history").findValuesAsText("to_status")).containsExactly(
                "QUEUED", "CLASSIFYING", "INVESTIGATING", "REVIEWING", "INVESTIGATING", "REVIEWING", "ASSIGNED",
                "ACCEPTED");
    }

    @Test
    void 정비팀이_거절하면_사람검토로_가고_어드민이_다른_부서로_수동배정한다() throws Exception {
        UUID requestId = submitRequest(login("reporter@kal.test"), "엔진 쪽 진동");
        sendEvent(requestId, "COMPLETED", assign("HYD"));

        String hyd = login("hyd@mro.test");
        String assignmentId = body(mvc.perform(as(hyd, get("/api/v1/assignments"))))
                .get("items").get(0).get("id").asText();
        mvc.perform(as(hyd, post("/api/v1/assignments/{id}/reject", assignmentId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", " "))))
                .andExpect(status().isBadRequest());
        mvc.perform(as(hyd, post("/api/v1/assignments/{id}/reject", assignmentId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "엔진 계통 증상"))))
                .andExpect(status().isOk());

        assertThat(requestStatus(requestId)).isEqualTo("HUMAN_REVIEW");
        // 거절 후에는 HYD가 더 이상 볼 수 없음
        mvc.perform(as(hyd, get("/api/v1/requests/{id}", requestId))).andExpect(status().isNotFound());

        String admin = login("admin@mro.test");
        JsonNode reviews = body(mvc.perform(as(admin, get("/api/v1/reviews").param("status", "OPEN")))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].reason").value("TEAM_REJECTED")));
        String reviewId = reviews.get("items").get(0).get("id").asText();

        mvc.perform(as(admin, post("/api/v1/reviews/{id}/resolve", reviewId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "ASSIGN", "department_code", "NOPE"))))
                .andExpect(status().isBadRequest());
        mvc.perform(as(admin, post("/api/v1/reviews/{id}/resolve", reviewId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "ASSIGN", "department_code", "ENG", "note", "엔진팀 이관"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(as(admin, post("/api/v1/reviews/{id}/resolve", reviewId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("action", "CLOSE"))))
                .andExpect(status().isConflict());

        String eng = login("eng@mro.test");
        JsonNode engInbox = body(mvc.perform(as(eng, get("/api/v1/assignments").param("status", "PENDING")))
                .andExpect(jsonPath("$.items[0].assigned_by").value("ADMIN")));
        mvc.perform(as(eng, post("/api/v1/assignments/{id}/accept", engInbox.get("items").get(0).get("id").asText())))
                .andExpect(status().isOk());
        assertThat(requestStatus(requestId)).isEqualTo("ACCEPTED");
        mvc.perform(as(admin, get("/api/v1/requests/{id}", requestId)))
                .andExpect(jsonPath("$.review_reason").isEmpty());
    }

    @Test
    void AI가_없는_부서를_제안하면_배정하지_않고_VALIDATION_FAILED로_사람검토() throws Exception {
        UUID requestId = submitRequest(login("reporter@kal.test"), "누유");
        sendEvent(requestId, "COMPLETED", Map.of("outcome", "ASSIGN", "department", "유압팀"));

        mvc.perform(as(login("admin@mro.test"), get("/api/v1/requests/{id}", requestId)))
                .andExpect(jsonPath("$.status").value("HUMAN_REVIEW"))
                .andExpect(jsonPath("$.review_reason").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.assigned_department").isEmpty());
        assertThat(count("select count(*) from assignments")).isZero();
    }

    @Test
    void 재조사_한도초과와_FAILED는_사유와_함께_사람검토로_간다() throws Exception {
        String reporter = login("reporter@kal.test");
        UUID retry = submitRequest(reporter, "원인 불명 경고등");
        sendEvent(retry, "COMPLETED", Map.of("outcome", "HUMAN_REVIEW", "review_reason", "RETRY_LIMIT",
                "summary", "재조사 2회 후에도 근거 부족"));
        UUID failed = submitRequest(reporter, "원인 불명 경고등");
        sendEvent(failed, "FAILED", Map.of("error", "LLM timeout"));

        String admin = login("admin@mro.test");
        mvc.perform(as(admin, get("/api/v1/requests/{id}", retry)))
                .andExpect(jsonPath("$.review_reason").value("RETRY_LIMIT"))
                .andExpect(jsonPath("$.summary").value("재조사 2회 후에도 근거 부족"));
        mvc.perform(as(admin, get("/api/v1/requests/{id}", failed)))
                .andExpect(jsonPath("$.review_reason").value("SYSTEM_ERROR"));
    }

    @Test
    void 처리할_수_없는_이벤트는_재시도_없이_이벤트_DLQ로_가고_수정해_다시_보내면_반영된다() throws Exception {
        UUID requestId = submitRequest(login("reporter@kal.test"), "누유");
        UUID eventId = UUID.randomUUID();

        publishEvent(requestId, eventId, "STAGE_STARTED", Map.of("stage", "THINKING"));
        Message dead = rabbitTemplate.receive(RabbitConfig.EVENT_DLQ, WAIT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(objectMapper.readTree(dead.getBody()).get("event_id").asText()).isEqualTo(eventId.toString());
        List<Map<String, ?>> xDeath = dead.getMessageProperties().getXDeathHeader();
        assertThat(xDeath.get(0).get("reason")).isEqualTo("rejected");
        assertThat(xDeath.get(0).get("count")).isEqualTo(1L);
        assertThat(count("select count(*) from agent_events")).isZero();

        // 같은 event_id로 고쳐서 보내면 정상 반영
        publishEvent(requestId, eventId, "STAGE_STARTED", Map.of("stage", "CLASSIFYING"));
        awaitEventStored(eventId);
        assertThat(requestStatus(requestId)).isEqualTo("CLASSIFYING");
    }

    @Test
    void 없는_요청의_이벤트와_JSON이_아닌_메시지도_이벤트_DLQ로_간다() throws Exception {
        publishEvent(UUID.randomUUID(), UUID.randomUUID(), "STAGE_STARTED", Map.of("stage", "CLASSIFYING"));
        assertThat(rabbitTemplate.receive(RabbitConfig.EVENT_DLQ, WAIT.toMillis())).isNotNull();

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.EVENT_ROUTING_KEY,
                new Message("{not json".getBytes(StandardCharsets.UTF_8), properties));
        Message dead = rabbitTemplate.receive(RabbitConfig.EVENT_DLQ, WAIT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("{not json");
    }

    @Test
    void AI서버가_실행요청을_거절하면_실행_DLQ를_거쳐_SYSTEM_ERROR_사람검토로_간다() throws Exception {
        UUID requestId = submitRequest(login("reporter@kal.test"), "누유");

        // AI 서버 역할: 실행 요청을 가져와 requeue 없이 거절
        await().atMost(WAIT).until(() -> rabbitTemplate.execute(channel -> {
            GetResponse response = channel.basicGet(RabbitConfig.RUN_QUEUE, false);
            if (response == null) {
                return false;
            }
            channel.basicReject(response.getEnvelope().getDeliveryTag(), false);
            return true;
        }));

        await().atMost(WAIT).until(() -> "HUMAN_REVIEW".equals(requestStatus(requestId)));
        mvc.perform(as(login("admin@mro.test"), get("/api/v1/reviews").param("status", "OPEN")))
                .andExpect(jsonPath("$.items[0].reason").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.items[0].note").value("AI 실행 요청이 DLQ로 이동 (AI 서버가 처리하지 못하고 거절)"));
    }

    @Test
    void 아무도_가져가지_않아_만료된_실행요청도_사람검토로_간다() throws Exception {
        UUID requestId = submitRequest(login("reporter@kal.test"), "누유");
        Message original = rabbitTemplate.receive(RabbitConfig.RUN_QUEUE, WAIT.toMillis());
        assertThat(original).isNotNull();

        // 큐 TTL(5분)을 기다리는 대신 메시지 단위 TTL로 즉시 만료시킵니다.
        original.getMessageProperties().setExpiration("1");
        rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.RUN_ROUTING_KEY, original);

        await().atMost(WAIT).until(() -> "HUMAN_REVIEW".equals(requestStatus(requestId)));
        assertThat(jdbc.queryForObject("select note from human_reviews where request_id = ?", String.class,
                requestId)).contains("처리 시간 안에 AI 서버가 가져가지 않음");
    }

    @Test
    void 시간초과된_요청은_SYSTEM_ERROR로_넘기고_늦게_온_결과는_무시한다() throws Exception {
        UUID requestId = submitRequest(login("reporter@kal.test"), "누유");
        jdbc.update("update repair_requests set created_at = now() - interval '10 minutes' where id = ?", requestId);

        timeoutJob.expireStaleRuns();

        mvc.perform(as(login("admin@mro.test"), get("/api/v1/requests/{id}", requestId)))
                .andExpect(jsonPath("$.status").value("HUMAN_REVIEW"))
                .andExpect(jsonPath("$.review_reason").value("SYSTEM_ERROR"));
        sendEvent(requestId, "COMPLETED", assign("HYD"));
        assertThat(requestStatus(requestId)).isEqualTo("HUMAN_REVIEW");
        assertThat(count("select count(*) from assignments")).isZero();
    }

    @Test
    void 어드민_통계는_상태별_부서별_건수와_자동배정률을_준다() throws Exception {
        String reporter = login("reporter@kal.test");
        UUID assigned = submitRequest(reporter, "누유");
        sendEvent(assigned, "COMPLETED", assign("HYD"));
        UUID review = submitRequest(reporter, "원인 불명");
        sendEvent(review, "COMPLETED", Map.of("outcome", "HUMAN_REVIEW"));

        mvc.perform(as(login("admin@mro.test"), get("/api/v1/stats/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.by_status.ASSIGNED").value(1))
                .andExpect(jsonPath("$.by_status.HUMAN_REVIEW").value(1))
                .andExpect(jsonPath("$.by_department.HYD").value(1))
                .andExpect(jsonPath("$.auto_assigned").value(1))
                .andExpect(jsonPath("$.human_reviewed").value(1))
                .andExpect(jsonPath("$.auto_assign_rate").value(0.5));
    }
}
