package com.skala.mro.domain.review.dto;

import com.skala.mro.domain.review.entity.ReviewAction;

/** ASSIGN이면 department_code 필수. */
public record ResolveRequest(ReviewAction action, String departmentCode, String note) {
}
