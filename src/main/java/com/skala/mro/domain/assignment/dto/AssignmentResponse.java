package com.skala.mro.domain.assignment.dto;

import com.skala.mro.domain.assignment.entity.AssignedBy;
import com.skala.mro.domain.assignment.entity.Assignment;
import com.skala.mro.domain.assignment.entity.AssignmentStatus;
import com.skala.mro.domain.request.dto.RequestBrief;
import com.skala.mro.domain.request.entity.RepairRequest;
import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(UUID id, UUID requestId, String departmentCode, AssignmentStatus status,
                                 AssignedBy assignedBy, String rejectReason, Instant createdAt,
                                 Instant respondedAt, RequestBrief request) {

    public static AssignmentResponse of(Assignment a, RepairRequest r) {
        return new AssignmentResponse(a.getId(), a.getRequestId(), a.getDepartmentCode(), a.getStatus(),
                a.getAssignedBy(), a.getRejectReason(), a.getCreatedAt(), a.getRespondedAt(),
                RequestBrief.from(r));
    }
}
