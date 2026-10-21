package com.skala.mro.domain.request.dto;

import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.entity.RequestStatus;
import com.skala.mro.domain.request.entity.ReviewReason;
import java.time.Instant;
import java.util.UUID;

public record RepairRequestResponse(UUID id, String tailNumber, String aircraftModel, String airlineCode,
                                    String symptomText, RequestStatus status, ReviewReason reviewReason,
                                    String assignedDepartment, String summary, Instant createdAt,
                                    Instant updatedAt) {

    public static RepairRequestResponse from(RepairRequest r) {
        return new RepairRequestResponse(r.getId(), r.getTailNumber(), r.getAircraftModel(), r.getAirlineCode(),
                r.getSymptomText(), r.getStatus(), r.getReviewReason(), r.getAssignedDepartment(),
                r.getSummary(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
