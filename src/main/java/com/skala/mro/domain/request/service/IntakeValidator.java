package com.skala.mro.domain.request.service;

import com.skala.mro.domain.master.entity.Aircraft;
import com.skala.mro.domain.master.repository.AircraftRepository;
import com.skala.mro.domain.request.dto.CreateRepairRequest;
import com.skala.mro.domain.request.dto.IntakeIssue;
import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.error.ErrorCode;
import com.skala.mro.global.security.AuthUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 흐름도의 "Backend 확인": 기체 마스터 대조 · 항공사 · 정확한 모델.
 * 누락·충돌이 있으면 AI 작업을 만들지 않고 422 NEEDS_SUPPLEMENT로 즉시 응답합니다.
 */
@Component
@RequiredArgsConstructor
public class IntakeValidator {

    private final AircraftRepository aircraftRepository;

    public Aircraft validate(CreateRepairRequest request, AuthUser reporter) {
        List<IntakeIssue> issues = new ArrayList<>();
        requireText(request.tailNumber(), "tail_number", issues);
        requireText(request.airlineCode(), "airline_code", issues);
        requireText(request.aircraftModel(), "aircraft_model", issues);
        requireText(request.symptomText(), "symptom_text", issues);
        throwIfAny(issues);

        Aircraft aircraft = aircraftRepository.findById(normalizeTailNumber(request.tailNumber()))
                .orElseThrow(() -> supplement(List.of(IntakeIssue.of("tail_number", "NOT_FOUND"))));

        // 다른 항공사 기체면 모델 등 상세 정보를 돌려주지 않습니다.
        if (!aircraft.getAirlineCode().equals(reporter.airlineCode())) {
            throw supplement(List.of(IntakeIssue.of("tail_number", "FORBIDDEN_AIRLINE")));
        }
        if (!aircraft.isActive()) {
            issues.add(IntakeIssue.of("tail_number", "INACTIVE"));
        }
        if (!aircraft.getAirlineCode().equalsIgnoreCase(request.airlineCode().trim())) {
            issues.add(new IntakeIssue("airline_code", "MISMATCH", aircraft.getAirlineCode()));
        }
        if (!aircraft.getAircraftModel().equalsIgnoreCase(request.aircraftModel().trim())) {
            issues.add(new IntakeIssue("aircraft_model", "MISMATCH", aircraft.getAircraftModel()));
        }
        throwIfAny(issues);
        return aircraft;
    }

    /** "hl-7701 " → "HL7701" */
    static String normalizeTailNumber(String tailNumber) {
        return tailNumber.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private static void requireText(String value, String field, List<IntakeIssue> issues) {
        if (value == null || value.isBlank()) {
            issues.add(IntakeIssue.of(field, "REQUIRED"));
        }
    }

    private static void throwIfAny(List<IntakeIssue> issues) {
        if (!issues.isEmpty()) {
            throw supplement(issues);
        }
    }

    private static ApiException supplement(List<IntakeIssue> issues) {
        return new ApiException(ErrorCode.NEEDS_SUPPLEMENT, ErrorCode.NEEDS_SUPPLEMENT.getDefaultMessage(), issues);
    }
}
