package com.skala.mro;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthAndAccessTest extends IntegrationTestSupport {

    @Test
    void 로그인하면_토큰과_사용자정보를_snake_case로_돌려준다() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "hyd@mro.test", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("bearer"))
                .andExpect(jsonPath("$.expires_in").value(28800))
                .andExpect(jsonPath("$.user.role").value("MAINTENANCE"))
                .andExpect(jsonPath("$.user.department_code").value("HYD"));
    }

    @Test
    void 비밀번호가_틀리면_401_공통에러형식() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "hyd@mro.test", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 토큰이_없거나_잘못되면_401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비활성화된_계정은_기존_토큰도_막힌다() throws Exception {
        String token = login("reporter@kal.test");
        jdbc.update("update users set active = false where email = 'reporter@kal.test'");
        mvc.perform(as(token, get("/api/v1/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void 내부_경로는_막혀있다() throws Exception {
        mvc.perform(post("/internal/requests/x/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void 역할이_맞지_않는_API는_403() throws Exception {
        String reporter = login("reporter@kal.test");
        String maintenance = login("hyd@mro.test");

        mvc.perform(as(maintenance, post("/api/v1/requests")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tail_number", "HL7701"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(as(reporter, get("/api/v1/reviews"))).andExpect(status().isForbidden());
        mvc.perform(as(reporter, get("/api/v1/assignments"))).andExpect(status().isForbidden());
        mvc.perform(as(reporter, get("/api/v1/stats/summary"))).andExpect(status().isForbidden());
        mvc.perform(as(maintenance, get("/api/v1/reviews"))).andExpect(status().isForbidden());
    }

    @Test
    void 다른_사람의_요청과_다른_부서의_요청은_404() throws Exception {
        String reporter = login("reporter@kal.test");
        UUID requestId = submitRequest(reporter, "좌측 메인기어 유압유 누유");
        sendEvent(requestId, "COMPLETED", assign("HYD"));

        mvc.perform(as(login("reporter2@kal.test"), get("/api/v1/requests/{id}", requestId)))
                .andExpect(status().isNotFound());
        mvc.perform(as(login("eng@mro.test"), get("/api/v1/requests/{id}", requestId)))
                .andExpect(status().isNotFound());
        mvc.perform(as(login("eng@mro.test"), get("/api/v1/requests/{id}/trace", requestId)))
                .andExpect(status().isNotFound());

        mvc.perform(as(reporter, get("/api/v1/requests/{id}", requestId))).andExpect(status().isOk());
        mvc.perform(as(login("hyd@mro.test"), get("/api/v1/requests/{id}/trace", requestId)))
                .andExpect(status().isOk());
        // 신고자는 판단 근거(trace)를 볼 수 없음
        mvc.perform(as(reporter, get("/api/v1/requests/{id}/trace", requestId))).andExpect(status().isForbidden());
    }

    @Test
    void 목록은_역할별_범위로만_조회된다() throws Exception {
        String reporter = login("reporter@kal.test");
        submitRequest(reporter, "증상 1");
        submitRequest(login("reporter2@kal.test"), "증상 2");

        mvc.perform(as(reporter, get("/api/v1/requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(as(login("hyd@mro.test"), get("/api/v1/requests")))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(as(login("admin@mro.test"), get("/api/v1/requests")))
                .andExpect(jsonPath("$.total").value(2));
    }
}
