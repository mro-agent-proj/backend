package com.skala.mro.domain.request.dto;

import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.entity.RequestStatus;
import java.util.UUID;

/** 배정·검토 목록에서 요청을 함께 보여줄 때 쓰는 요약. */
public record RequestBrief(UUID id, String tailNumber, String aircraftModel, String airlineCode,
                           String symptomText, String summary, RequestStatus status) {

    public static RequestBrief from(RepairRequest r) {
        if (r == null) {
            return null;
        }
        return new RequestBrief(r.getId(), r.getTailNumber(), r.getAircraftModel(), r.getAirlineCode(),
                r.getSymptomText(), r.getSummary(), r.getStatus());
    }
}
