package com.skala.mro.domain.review.service;

import com.skala.mro.domain.request.entity.Actor;
import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.entity.RequestStatus;
import com.skala.mro.domain.request.repository.RepairRequestRepository;
import com.skala.mro.domain.request.service.RequestWorkflow;
import com.skala.mro.domain.review.dto.ResolveRequest;
import com.skala.mro.domain.review.dto.ReviewResponse;
import com.skala.mro.domain.review.entity.HumanReview;
import com.skala.mro.domain.review.entity.ReviewStatus;
import com.skala.mro.domain.review.repository.HumanReviewRepository;
import com.skala.mro.global.common.PageResponse;
import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.error.ErrorCode;
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

/** 사람 검토 큐. 처리 결과는 수동 배정 또는 종결이며, 그래프로 다시 돌리지 않습니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final HumanReviewRepository reviewRepository;
    private final RepairRequestRepository requestRepository;
    private final RequestWorkflow workflow;

    public PageResponse<ReviewResponse> list(ReviewStatus status, Pageable pageable) {
        Page<HumanReview> page = status == null
                ? reviewRepository.findAll(pageable)
                : reviewRepository.findByStatus(status, pageable);
        Map<UUID, RepairRequest> requests = requestRepository
                .findAllById(page.getContent().stream().map(HumanReview::getRequestId).toList()).stream()
                .collect(Collectors.toMap(RepairRequest::getId, Function.identity()));
        return PageResponse.of(page, v -> ReviewResponse.of(v, requests.get(v.getRequestId())));
    }

    @Transactional
    public ReviewResponse resolve(AuthUser admin, UUID reviewId, ResolveRequest command) {
        if (command == null || command.action() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "action(ASSIGN 또는 CLOSE)이 필요합니다.");
        }
        HumanReview review = reviewRepository.findById(reviewId).orElseThrow(ApiException::notFound);
        RepairRequest request = requestRepository.findById(review.getRequestId()).orElseThrow(ApiException::notFound);
        Actor actor = Actor.user(admin.id());

        String department = null;
        switch (command.action()) {
            case ASSIGN -> {
                department = command.departmentCode();
                if (department == null || department.isBlank()) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR, "ASSIGN에는 department_code가 필요합니다.");
                }
                workflow.assign(request, department, actor, null);
            }
            case CLOSE -> workflow.transition(request, RequestStatus.CLOSED, actor, command.note());
        }
        review.resolve(command.action(), department, command.note(), admin.id());
        return ReviewResponse.of(review, request);
    }
}
