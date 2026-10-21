package com.skala.mro.domain.request.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "request_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID requestId;

    @Enumerated(EnumType.STRING)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private RequestStatus toStatus;

    @Enumerated(EnumType.STRING)
    private ReviewReason reviewReason;

    @Enumerated(EnumType.STRING)
    private Actor.ActorType actorType;

    private UUID actorId;

    private String note;

    private Instant createdAt;

    public RequestStatusHistory(UUID requestId, RequestStatus from, RequestStatus to, ReviewReason reason, Actor actor,
                         String note) {
        this.requestId = requestId;
        this.fromStatus = from;
        this.toStatus = to;
        this.reviewReason = reason;
        this.actorType = actor.type();
        this.actorId = actor.userId();
        this.note = note;
        this.createdAt = Instant.now();
    }
}
