package com.skala.mro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

// 테스트에서는 지표 내보내기가 기본으로 꺼져 있어 명시적으로 켭니다.
@AutoConfigureObservability
class MetricsTest extends IntegrationTestSupport {

    @Test
    void 프로메테우스_엔드포인트는_인증없이_요청_상태전이_지표를_노출한다() throws Exception {
        submitRequest(login("reporter@kal.test"), "좌측 메인기어 유압유 누유");

        String body = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("jvm_memory_used_bytes")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).containsPattern(
                "mro_request_transitions_total\\{actor=\"[A-Z_]+\",[^}]*reason=\"NONE\",to=\"QUEUED\"\\} [1-9]");
    }
}
