package com.skala.mro.domain.assignment.service;

import com.skala.mro.domain.assignment.dto.AssignmentResponse;
import com.skala.mro.domain.assignment.entity.Assignment;
import com.skala.mro.domain.assignment.entity.AssignmentStatus;
import com.skala.mro.domain.assignment.repository.AssignmentRepository;
import com.skala.mro.domain.request.entity.Actor;
import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.entity.RequestStatus;
import com.skala.mro.domain.request.entity.ReviewReason;
import com.skala.mro.domain.request.repository.RepairRequestRepository;
import com.skala.mro.domain.request.service.RequestWorkflow;
import com.skala.mro.global.common.PageResponse;
import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.security.AuthUser;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final RepairRequestRepository requestRepository;
    private final RequestWorkflow workflow;

    /** 정비팀 대기함: 본인 부서의 배정만. */
    public PageResponse<AssignmentResponse> list(AuthUser user, AssignmentStatus status, Pageable pageable) {
        Page<Assignment> page = status == null
                ? assignmentRepository.findByDepartmentCode(user.departmentCode(), pageable)
                : assignmentRepository.findByDepartmentCodeAndStatus(user.departmentCode(), status, pageable);
        Map<UUID, RepairRequest> requests = requestRepository
                .findAllById(page.getContent().stream().map(Assignment::getRequestId).toList()).stream()
                .collect(Collectors.toMap(RepairRequest::getId, Function.identity()));
        return PageResponse.of(page, a -> AssignmentResponse.of(a, requests.get(a.getRequestId())));
    }

    @Transactional
    public AssignmentResponse accept(AuthUser user, UUID assignmentId) {
        Assignment assignment = loadOwn(user, assignmentId);
        RepairRequest request = requestRepository.findById(assignment.getRequestId())
                .orElseThrow(ApiException::notFound);
        assignment.accept(user.id());
        workflow.transition(request, RequestStatus.ACCEPTED, Actor.user(user.id()), "정비팀 수락");
        return AssignmentResponse.of(assignment, request);
    }

    /** 흐름도: 정비팀 거절 → 사람 검토(TEAM_REJECTED). */
    @Transactional
    public AssignmentResponse reject(AuthUser user, UUID assignmentId, String reason) {
        Assignment assignment = loadOwn(user, assignmentId);
        RepairRequest request = requestRepository.findById(assignment.getRequestId())
                .orElseThrow(ApiException::notFound);
        assignment.reject(user.id(), reason);
        workflow.sendToHumanReview(request, ReviewReason.TEAM_REJECTED, Actor.user(user.id()),
                "정비팀(%s) 거절: %s".formatted(assignment.getDepartmentCode(), reason));
        return AssignmentResponse.of(assignment, request);
    }

    private Assignment loadOwn(AuthUser user, UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .filter(a -> a.getDepartmentCode().equals(user.departmentCode()))
                .orElseThrow(ApiException::notFound);
    }
}
