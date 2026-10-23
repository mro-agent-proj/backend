package com.skala.mro;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class StaffingTest extends IntegrationTestSupport {

    @BeforeEach
    void technicians() {
        jdbc.update("""
                insert into technicians (id, department_code, name, title, shift, on_duty, available, note) values
                  ('T-HYD-01', 'HYD', '황선우', '팀장', '주간', true, false, '작업 중'),
                  ('T-HYD-02', 'HYD', '마준서', '정비사', '주간', true, true, null),
                  ('T-HYD-03', 'HYD', '한예준', '정비사', '야간', false, false, null),
                  ('T-ENG-01', 'ENG', '정우진', '팀장', '주간', true, true, null)""");
    }

    @Test
    void 관리자는_부서별_인원_현황을_본다() throws Exception {
        mvc.perform(as(login("admin@mro.test"), get("/api/v1/staffing")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.on_duty").value(3))
                .andExpect(jsonPath("$.available").value(2))
                // 활성 부서 순서(AVI, ENG, HYD). 명단이 없는 부서도 0명으로 나온다
                .andExpect(jsonPath("$.departments[0].code").value("AVI"))
                .andExpect(jsonPath("$.departments[0].total").value(0))
                .andExpect(jsonPath("$.departments[2].code").value("HYD"))
                .andExpect(jsonPath("$.departments[2].name").value("유압·착륙장치"))
                .andExpect(jsonPath("$.departments[2].total").value(3))
                .andExpect(jsonPath("$.departments[2].on_duty").value(2))
                .andExpect(jsonPath("$.departments[2].available").value(1))
                .andExpect(jsonPath("$.departments[2].members[0].id").value("T-HYD-01"))
                .andExpect(jsonPath("$.departments[2].members[0].note").value("작업 중"));
    }

    @Test
    void 관리자_외에는_볼_수_없다() throws Exception {
        for (String email : new String[]{"hyd@mro.test", "reporter@kal.test"}) {
            mvc.perform(as(login(email), get("/api/v1/staffing")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Test
    void 출근하지_않은_사람은_작업가능일_수_없다() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into technicians (id, department_code, name, title, shift, on_duty, available)
                values ('T-AVI-01', 'AVI', '류시온', '팀장', '주간', false, true)"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
