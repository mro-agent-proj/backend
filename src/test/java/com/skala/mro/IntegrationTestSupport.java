package com.skala.mro;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.mro.domain.agent.config.RabbitConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres·RabbitMQ는 Testcontainers로 띄웁니다 (Docker 필요).
 * Postgres만 외부 DB를 쓰려면 TEST_DB_URL(+TEST_DB_USERNAME/PASSWORD)을 지정하세요 (pgvector 확장 필요).
 * AI 서버는 없고, 테스트가 mro.agent.events 큐에 직접 이벤트를 발행합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "test1234";
    protected static final Duration WAIT = Duration.ofSeconds(10);

    private static final PostgreSQLContainer<?> POSTGRES;
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");
    private static String passwordHash;

    static {
        if (System.getenv("TEST_DB_URL") == null) {
            POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:0.8.2-pg16")
                    .asCompatibleSubstituteFor("postgres"));
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
        RABBIT.start();
    }

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "mro"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "mro"));
        }
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetData() {
        for (String queue : new String[]{RabbitConfig.RUN_QUEUE, RabbitConfig.EVENT_QUEUE, RabbitConfig.RUN_DLQ,
                RabbitConfig.EVENT_DLQ}) {
            amqpAdmin.purgeQueue(queue, false);
        }
        jdbc.execute("""
                truncate table human_reviews, assignments, agent_assessments, agent_events, request_status_history,
                  repair_requests, users, maintenance_history, aircraft, departments, airlines,
                  knowledge_releases, tech_documents cascade""");
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        jdbc.update("insert into airlines (code, name) values ('KAL', '대한항공'), ('AAR', '아시아나항공')");
        jdbc.update("""
                insert into departments (code, name, ata_chapters) values
                  ('HYD', '유압·착륙장치', '29,32'), ('ENG', '엔진', '72'), ('AVI', '항전', '34')""");
        jdbc.update("""
                insert into aircraft (tail_number, msn, aircraft_model, airline_code, active) values
                  ('HL7701', '5123', 'A320-214', 'KAL', true),
                  ('HL7703', '4011', 'A320-214', 'KAL', false),
                  ('HL8001', '6201', 'A321-272N', 'AAR', true)""");
        jdbc.update("""
                insert into maintenance_history (id, tail_number, reported_at, symptom_text, ata_chapter, department_code)
                values ('MH-000001', 'HL7701', '2026-10-01T00:00:00Z', '좌측 메인기어 유압유 누유', '32', 'HYD'),
                       ('MH-000002', 'HL8001', '2026-10-02T00:00:00Z', '좌측 메인기어 유압유 누유', '32', 'HYD')""");
        user("reporter@kal.test", "REPORTER", null, "KAL");
        user("reporter2@kal.test", "REPORTER", null, "KAL");
        user("reporter@aar.test", "REPORTER", null, "AAR");
        user("hyd@mro.test", "MAINTENANCE", "HYD", null);
        user("eng@mro.test", "MAINTENANCE", "ENG", null);
        user("admin@mro.test", "ADMIN", null, null);
    }

    private void user(String email, String role, String department, String airline) {
        jdbc.update("""
                insert into users (id, email, name, password_hash, role, department_code, airline_code, active)
                values (?, ?, ?, ?, ?, ?, ?, true)""",
                UUID.randomUUID(), email, email, passwordHash, role, department, airline);
    }

    protected String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("access_token").asText();
    }

    protected MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    /** 신고자로 정상 접수하고 request_id를 돌려줍니다. */
    protected UUID submitRequest(String reporterToken, String symptom) throws Exception {
        ResultActions result = mvc.perform(as(reporterToken, post("/api/v1/requests"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tail_number", "HL7701", "airline_code", "KAL",
                                "aircraft_model", "A320-214", "symptom_text", symptom))))
                .andExpect(status().isAccepted());
        return UUID.fromString(body(result).get("id").asText());
    }

    /** AI 서버처럼 mro.agent.events에 이벤트를 발행만 합니다. */
    protected void publishEvent(UUID requestId, UUID eventId, String type, Object payload) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.EVENT_ROUTING_KEY,
                Map.of("event_id", eventId, "request_id", requestId, "type", type,
                        "occurred_at", "2026-10-21T03:00:00Z", "payload", payload));
    }

    /** 이벤트를 발행하고 Spring이 처리해 저장할 때까지 기다립니다. */
    protected void sendEvent(UUID requestId, String type, Object payload) {
        UUID eventId = UUID.randomUUID();
        publishEvent(requestId, eventId, type, payload);
        awaitEventStored(eventId);
    }

    protected void awaitEventStored(UUID eventId) {
        await().atMost(WAIT).until(() -> jdbc.queryForObject(
                "select count(*) from agent_events where event_id = ?", Integer.class, eventId) == 1);
    }

    /** 배정 COMPLETED payload. 근거는 접수 기체(HL7701, A320-214)의 수리이력 MH-000001. */
    protected static Map<String, Object> assign(String department) {
        return Map.of("outcome", "ASSIGN", "department", department, "summary", "누유",
                "evidence", List.of(Map.of("source", "HISTORY", "ref_id", "MH-000001")));
    }

    protected String requestStatus(UUID requestId) {
        return jdbc.queryForObject("select status from repair_requests where id = ?", String.class, requestId);
    }
}
