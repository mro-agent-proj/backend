package com.skala.mro.domain.request.dto;

/** 필수값 검증은 IntakeValidator가 NEEDS_SUPPLEMENT 형식으로 합니다. */
public record CreateRepairRequest(String tailNumber, String airlineCode, String aircraftModel, String symptomText) {
}
