package com.skala.mro.domain.assignment.entity;

import com.skala.mro.domain.request.entity.Actor;
import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.error.ErrorCode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

    @Id
    private UUID id;

    private UUID requestId;

    private String departmentCode;

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status;

    @Enumerated(EnumType.STRING)
    private AssignedBy assignedBy;

    private UUID assignedByUserId;

    private UUID respondedBy;

    private String rejectReason;

    private Instant createdAt;

    private Instant respondedAt;

    @Version
    private Long version;

    public static Assignment create(UUID requestId, String departmentCode, Actor actor) {
        Assignment assignment = new Assignment();
        assignment.id = UUID.randomUUID();
        assignment.requestId = requestId;
        assignment.departmentCode = departmentCode;
        assignment.status = AssignmentStatus.PENDING;
        assignment.assignedBy = actor.type() == Actor.ActorType.USER ? AssignedBy.ADMIN : AssignedBy.AI;
        assignment.assignedByUserId = actor.userId();
        assignment.createdAt = Instant.now();
        return assignment;
    }

    public void accept(UUID userId) {
        respond(AssignmentStatus.ACCEPTED, userId, null);
    }

    public void reject(UUID userId, String reason) {
        respond(AssignmentStatus.REJECTED, userId, reason);
    }

    private void respond(AssignmentStatus to, UUID userId, String reason) {
        if (status != AssignmentStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_STATE, "이미 응답한 배정입니다.");
        }
        this.status = to;
        this.respondedBy = userId;
        this.rejectReason = reason;
        this.respondedAt = Instant.now();
    }
}
