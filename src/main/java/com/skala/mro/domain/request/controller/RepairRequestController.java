package com.skala.mro.domain.request.controller;

import com.skala.mro.domain.request.dto.CreateRepairRequest;
import com.skala.mro.domain.request.dto.RepairRequestResponse;
import com.skala.mro.domain.request.dto.RequestSearch;
import com.skala.mro.domain.request.dto.TraceResponse;
import com.skala.mro.domain.request.entity.RequestStatus;
import com.skala.mro.domain.request.service.RepairRequestService;
import com.skala.mro.global.common.PageResponse;
import com.skala.mro.global.security.AuthUser;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RepairRequestController {

    private final RepairRequestService service;

    /** 202 + QUEUED. 누락·충돌이면 422 NEEDS_SUPPLEMENT (요청은 저장되지 않음). */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('REPORTER')")
    public RepairRequestResponse create(@AuthenticationPrincipal AuthUser user,
                                        @RequestBody CreateRepairRequest request) {
        return service.create(user, request);
    }

    /** 역할별로 볼 수 있는 범위 안에서만 조회됩니다. */
    @GetMapping
    public PageResponse<RepairRequestResponse> list(@AuthenticationPrincipal AuthUser user,
                                                    @RequestParam(defaultValue = "false") boolean mine,
                                                    @RequestParam(required = false) RequestStatus status,
                                                    @RequestParam(required = false) String department,
                                                    @RequestParam(required = false) Instant from,
                                                    @RequestParam(required = false) Instant to,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return service.list(user, new RequestSearch(mine, status, department, from, to),
                PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    public RepairRequestResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return service.get(user, id);
    }

    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAnyRole('MAINTENANCE', 'ADMIN')")
    public TraceResponse trace(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return service.trace(user, id);
    }
}
