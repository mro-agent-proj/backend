package com.skala.mro.domain.assignment.controller;

import com.skala.mro.domain.assignment.dto.AssignmentResponse;
import com.skala.mro.domain.assignment.dto.RejectRequest;
import com.skala.mro.domain.assignment.entity.AssignmentStatus;
import com.skala.mro.domain.assignment.service.AssignmentService;
import com.skala.mro.global.common.PageResponse;
import com.skala.mro.global.security.AuthUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assignments")
@PreAuthorize("hasRole('MAINTENANCE')")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping
    public PageResponse<AssignmentResponse> list(@AuthenticationPrincipal AuthUser user,
                                                 @RequestParam(required = false) AssignmentStatus status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return assignmentService.list(user, status,
                PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping("/{id}/accept")
    public AssignmentResponse accept(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return assignmentService.accept(user, id);
    }

    @PostMapping("/{id}/reject")
    public AssignmentResponse reject(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                     @Valid @RequestBody RejectRequest request) {
        return assignmentService.reject(user, id, request.reason().trim());
    }
}
