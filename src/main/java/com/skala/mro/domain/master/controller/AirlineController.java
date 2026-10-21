package com.skala.mro.domain.master.controller;

import com.skala.mro.domain.master.dto.AirlineResponse;
import com.skala.mro.domain.master.service.AirlineService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 가입 화면에서 쓰므로 인증 없이 열려 있습니다 (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/airlines")
@RequiredArgsConstructor
public class AirlineController {

    private final AirlineService airlineService;

    @GetMapping
    public List<AirlineResponse> list() {
        return airlineService.list();
    }
}
