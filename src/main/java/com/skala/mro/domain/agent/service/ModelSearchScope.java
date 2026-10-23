package com.skala.mro.domain.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Retrieval grouping only, never a declaration of maintenance/configuration applicability. */
public final class ModelSearchScope {
    private ModelSearchScope() { }

    // Explicit catalogue: unlisted/ambiguous source labels stay exact-only. No A3* prefix expansion.
    private static final List<List<String>> GROUPS = List.of(
            List.of("A318111", "A318112", "A318121", "A318122"),
            List.of("A319111", "A319112", "A319113", "A319114", "A319115", "A319131", "A319132", "A319133"),
            List.of("A319151N", "A319153N", "A319171N", "A319173N"),
            List.of("A320211", "A320212", "A320214", "A320215", "A320216", "A320231", "A320232", "A320233"),
            List.of("A320251N", "A320252N", "A320253N", "A320271N", "A320272N", "A320273N"),
            List.of("A321111", "A321112", "A321131", "A321211", "A321212", "A321213", "A321231", "A321232"),
            List.of("A321251N", "A321252N", "A321253N", "A321271N", "A321272N")
    );

    public static String json(String requestedModel) {
        List<String> group = GROUPS.stream().filter(g -> g.contains(requestedModel)).findFirst().orElse(null);
        Map<String, Object> policy = group == null
                ? Map.of("policy_version", "airbus-model-scope-v1", "default_level", "EXACT",
                         "levels", Map.of("EXACT", List.of(requestedModel)))
                : Map.of("policy_version", "airbus-model-scope-v1", "default_level", "MODEL_GENERATION",
                         "levels", Map.of("EXACT", List.of(requestedModel), "MODEL_GENERATION", group,
                                          "FAMILY", new TreeSet<>(GROUPS.stream().flatMap(List::stream).toList())));
        try {
            return new ObjectMapper().writeValueAsString(policy);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode model search policy", e);
        }
    }
}
