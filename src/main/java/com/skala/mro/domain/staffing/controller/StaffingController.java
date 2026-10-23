package com.skala.mro.domain.staffing.controller;

import com.skala.mro.domain.staffing.dto.StaffingResponse;
import com.skala.mro.domain.staffing.service.StaffingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 정비 인원 현황판 (참고용, 조회만). 배정을 맡는 관리자 화면에서만 씁니다. */
@RestController
@RequestMapping("/api/v1/staffing")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StaffingController {

    private final StaffingService staffingService;

    @GetMapping
    public StaffingResponse summary() {
        return staffingService.summary();
    }
}
