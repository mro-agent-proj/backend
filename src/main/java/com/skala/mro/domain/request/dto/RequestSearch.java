package com.skala.mro.domain.request.dto;

import com.skala.mro.domain.request.entity.RequestStatus;
import java.time.Instant;

public record RequestSearch(boolean mine, RequestStatus status, String department, Instant from, Instant to) {
}
