package com.skala.mro.domain.agent.dto;

import com.skala.mro.domain.request.entity.RepairRequest;
import java.util.UUID;

/** mro.agent.run 큐 메시지 (Spring → AI 서버). */
public record RunPayload(UUID requestId, String tailNumber, String aircraftModel, String airlineCode,
                         String symptomText) {

    public static RunPayload from(RepairRequest r) {
        return new RunPayload(r.getId(), r.getTailNumber(), r.getAircraftModel(), r.getAirlineCode(),
                r.getSymptomText());
    }
}
