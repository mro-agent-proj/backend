package com.skala.mro.domain.request.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 422 NEEDS_SUPPLEMENT의 details 항목. issue: REQUIRED / NOT_FOUND / INACTIVE / MISMATCH / FORBIDDEN_AIRLINE */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntakeIssue(String field, String issue, String expected) {

    public static IntakeIssue of(String field, String issue) {
        return new IntakeIssue(field, issue, null);
    }
}
