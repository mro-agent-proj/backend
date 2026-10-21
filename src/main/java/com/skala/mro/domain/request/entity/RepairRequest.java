package com.skala.mro.domain.request.entity;

import com.skala.mro.domain.master.entity.Aircraft;
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

/** 상태·배정 변경은 RequestWorkflow를 통해서만 합니다 (이력 기록과 전이 검증을 위해). */
@Entity
@Table(name = "repair_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepairRequest {

    @Id
    private UUID id;

    private UUID reporterId;

    private String tailNumber;

    private String aircraftModel;

    private String airlineCode;

    private String symptomText;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    @Enumerated(EnumType.STRING)
    private ReviewReason reviewReason;

    private String assignedDepartment;

    private String summary;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    public static RepairRequest create(UUID reporterId, Aircraft aircraft, String symptomText) {
        RepairRequest request = new RepairRequest();
        request.id = UUID.randomUUID();
        request.reporterId = reporterId;
        request.tailNumber = aircraft.getTailNumber();
        request.aircraftModel = aircraft.getAircraftModel();
        request.airlineCode = aircraft.getAirlineCode();
        request.symptomText = symptomText;
        request.status = RequestStatus.QUEUED;
        request.createdAt = Instant.now();
        request.updatedAt = request.createdAt;
        return request;
    }

    /** RequestWorkflow에서만 호출합니다. */
    public void changeStatus(RequestStatus to, ReviewReason reason) {
        this.status = to;
        this.reviewReason = to == RequestStatus.HUMAN_REVIEW ? reason : null;
        if (to == RequestStatus.HUMAN_REVIEW) {
            // 거절·검증 실패로 넘어오면 기존 배정 부서는 더 이상 유효하지 않음
            this.assignedDepartment = null;
        }
        this.updatedAt = Instant.now();
    }

    /** RequestWorkflow에서만 호출합니다. */
    public void assignTo(String departmentCode) {
        this.assignedDepartment = departmentCode;
    }

    /** RequestWorkflow에서만 호출합니다. */
    public void updateSummary(String summary) {
        if (summary != null && !summary.isBlank()) {
            this.summary = summary;
        }
    }
}
