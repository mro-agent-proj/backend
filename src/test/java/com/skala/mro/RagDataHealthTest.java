package com.skala.mro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class RagDataHealthTest extends IntegrationTestSupport {

    @Test
    void RAG_데이터가_없으면_ragData는_UNKNOWN이고_전체_헬스는_UP() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.ragData.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.rabbit.status").value("UP"));
    }
}
