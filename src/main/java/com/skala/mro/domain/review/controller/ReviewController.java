package com.skala.mro.domain.review.controller;

import com.skala.mro.domain.review.dto.ResolveRequest;
import com.skala.mro.domain.review.dto.ReviewResponse;
import com.skala.mro.domain.review.entity.ReviewStatus;
import com.skala.mro.domain.review.service.ReviewService;
import com.skala.mro.global.common.PageResponse;
import com.skala.mro.global.security.AuthUser;
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
@RequestMapping("/api/v1/reviews")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public PageResponse<ReviewResponse> list(@RequestParam(required = false) ReviewStatus status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return reviewService.list(status, PageResponse.pageable(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));
    }

    @PostMapping("/{id}/resolve")
    public ReviewResponse resolve(@AuthenticationPrincipal AuthUser admin, @PathVariable UUID id,
                                  @RequestBody ResolveRequest request) {
        return reviewService.resolve(admin, id, request);
    }
}
