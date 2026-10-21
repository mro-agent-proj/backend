package com.skala.mro.domain.request.service;

import com.skala.mro.domain.request.entity.RepairRequest;
import com.skala.mro.domain.request.repository.RepairRequestRepository;
import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.security.AuthUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * 요청의 행 단위 접근 규칙 (인터페이스 규약 5번 섹션).
 * 신고자: 본인이 낸 요청 / 정비팀: 본인 부서에 배정된 요청 / 어드민: 전체.
 */
@Component
@RequiredArgsConstructor
public class RequestAccessPolicy {

    private final RepairRequestRepository requestRepository;

    public boolean canView(AuthUser user, RepairRequest request) {
        return switch (user.role()) {
            case ADMIN -> true;
            case REPORTER -> request.getReporterId().equals(user.id());
            case MAINTENANCE -> user.departmentCode() != null
                    && user.departmentCode().equals(request.getAssignedDepartment());
        };
    }

    /** 볼 수 없는 요청은 존재하지 않는 것처럼 404를 던집니다. */
    public RepairRequest loadVisible(AuthUser user, UUID requestId) {
        return requestRepository.findById(requestId)
                .filter(request -> canView(user, request))
                .orElseThrow(ApiException::notFound);
    }

    public Specification<RepairRequest> visibleTo(AuthUser user) {
        return switch (user.role()) {
            case ADMIN -> (root, query, cb) -> cb.conjunction();
            case REPORTER -> (root, query, cb) -> cb.equal(root.get("reporterId"), user.id());
            case MAINTENANCE -> (root, query, cb) -> cb.equal(root.get("assignedDepartment"), user.departmentCode());
        };
    }
}
