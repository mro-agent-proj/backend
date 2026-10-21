package com.skala.mro.domain.request.service;

import com.skala.mro.domain.assignment.entity.Assignment;
import com.skala.mro.domain.assignment.repository.AssignmentRepository;
import com.skala.mro.domain.master.repository.DepartmentRepository;
import com.skala.mro.domain.request.entity.Actor;
import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.entity.RequestStatus;
import com.skala.mro.domain.request.entity.RequestStatusHistory;
import com.skala.mro.domain.request.entity.ReviewReason;
import com.skala.mro.domain.request.repository.RepairRequestRepository;
import com.skala.mro.domain.request.repository.RequestStatusHistoryRepository;
import com.skala.mro.domain.review.entity.HumanReview;
import com.skala.mro.domain.review.repository.HumanReviewRepository;
import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청 상태를 바꾸는 유일한 경로. 전이 검증, 상태 이력, 배정·사람 검토 생성을 한곳에서 처리합니다.
 * 호출하는 쪽의 트랜잭션에 참여합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RequestWorkflow {

    private final RepairRequestRepository requestRepository;
    private final RequestStatusHistoryRepository historyRepository;
    private final AssignmentRepository assignmentRepository;
    private final HumanReviewRepository reviewRepository;
    private final DepartmentRepository departmentRepository;

    public RepairRequest submit(RepairRequest request, Actor actor) {
        RepairRequest saved = requestRepository.save(request);
        historyRepository.save(new RequestStatusHistory(saved.getId(), null, saved.getStatus(), null, actor, "접수"));
        return saved;
    }

    public void transition(RepairRequest request, RequestStatus to, Actor actor, String note) {
        apply(request, to, null, actor, note);
    }

    /** 배정 실행: 부서 코드를 검증한 뒤 ASSIGNED로 바꾸고 응답 대기 배정을 만듭니다. */
    public Assignment assign(RepairRequest request, String departmentCode, Actor actor, String summary) {
        if (departmentCode == null || !departmentRepository.existsByCodeAndActiveTrue(departmentCode)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "존재하지 않거나 비활성인 부서입니다: " + departmentCode);
        }
        request.updateSummary(summary);
        apply(request, RequestStatus.ASSIGNED, null, actor, "배정: " + departmentCode);
        request.assignTo(departmentCode);
        return assignmentRepository.save(Assignment.create(request.getId(), departmentCode, actor));
    }

    public HumanReview sendToHumanReview(RepairRequest request, ReviewReason reason, Actor actor, String note) {
        apply(request, RequestStatus.HUMAN_REVIEW, reason, actor, note);
        return reviewRepository.save(HumanReview.open(request.getId(), reason, note));
    }

    public void updateSummary(RepairRequest request, String summary) {
        request.updateSummary(summary);
    }

    private void apply(RepairRequest request, RequestStatus to, ReviewReason reason, Actor actor, String note) {
        RequestStatus from = request.getStatus();
        if (!from.canTransitionTo(to)) {
            throw new ApiException(ErrorCode.INVALID_STATE,
                    "%s 상태에서 %s(으)로 바꿀 수 없습니다.".formatted(from, to));
        }
        request.changeStatus(to, reason);
        requestRepository.save(request);
        historyRepository.save(new RequestStatusHistory(request.getId(), from, to, reason, actor, note));
    }
}
