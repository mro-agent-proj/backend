package com.skala.mro.domain.stats.controller;

import com.skala.mro.domain.stats.dto.StatsSummary;
import com.skala.mro.domain.stats.service.StatsService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/summary")
    public StatsSummary summary(@RequestParam(required = false) Instant from,
                                @RequestParam(required = false) Instant to) {
        return statsService.summary(from, to);
    }
}
