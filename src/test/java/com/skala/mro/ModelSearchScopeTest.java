package com.skala.mro;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.mro.domain.agent.service.ModelSearchScope;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ModelSearchScopeTest {
    @Test void scopeSeparatesGenerationAndFamilyAndLeavesUnknownExact() throws Exception {
        var json = new ObjectMapper();
        var policy = json.readTree(ModelSearchScope.json("A320232"));
        assertThat(policy.path("default_level").asText()).isEqualTo("MODEL_GENERATION");
        assertThat(policy.path("levels").path("MODEL_GENERATION").toString()).contains("A320214", "A320232").doesNotContain("A320251N", "A321211");
        assertThat(policy.path("levels").path("FAMILY").toString()).contains("A320251N", "A321211").doesNotContain("A330343");
        assertThat(json.readTree(ModelSearchScope.json("A320251N")).path("levels").path("MODEL_GENERATION").toString()).doesNotContain("A320232");
        for (String unknown : new String[]{"A320", "A320UNKNOWN", "A330343"}) {
            var exact = json.readTree(ModelSearchScope.json(unknown));
            assertThat(exact.path("default_level").asText()).isEqualTo("EXACT");
            assertThat(exact.path("levels").size()).isEqualTo(1);
        }
    }
}
