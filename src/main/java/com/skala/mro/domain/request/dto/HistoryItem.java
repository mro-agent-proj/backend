package com.skala.mro.domain.request.dto;

import com.skala.mro.domain.request.entity.Actor;
import com.skala.mro.domain.request.entity.RequestStatus;
import com.skala.mro.domain.request.entity.RequestStatusHistory;
import com.skala.mro.domain.request.entity.ReviewReason;
import java.time.Instant;
import java.util.UUID;

public record HistoryItem(RequestStatus fromStatus, RequestStatus toStatus, ReviewReason reviewReason,
                          Actor.ActorType actorType, UUID actorId, String note, Instant createdAt) {

    public static HistoryItem from(RequestStatusHistory h) {
        return new HistoryItem(h.getFromStatus(), h.getToStatus(), h.getReviewReason(), h.getActorType(),
                h.getActorId(), h.getNote(), h.getCreatedAt());
    }
}
