package com.skala.mro.domain.review.dto;

import com.skala.mro.domain.request.dto.RequestBrief;
import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.entity.ReviewReason;
import com.skala.mro.domain.review.entity.HumanReview;
import com.skala.mro.domain.review.entity.ReviewAction;
import com.skala.mro.domain.review.entity.ReviewStatus;
import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(UUID id, UUID requestId, ReviewReason reason, ReviewStatus status, String note,
                             ReviewAction resolution, String departmentCode, String resolutionNote,
                             UUID resolvedBy, Instant createdAt, Instant resolvedAt, RequestBrief request) {

    public static ReviewResponse of(HumanReview v, RepairRequest r) {
        return new ReviewResponse(v.getId(), v.getRequestId(), v.getReason(), v.getStatus(), v.getNote(),
                v.getResolution(), v.getDepartmentCode(), v.getResolutionNote(), v.getResolvedBy(),
                v.getCreatedAt(), v.getResolvedAt(), RequestBrief.from(r));
    }
}
