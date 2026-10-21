package com.skala.mro.domain.request.dto;

import java.util.List;

public record TraceResponse(RepairRequestResponse request, List<AssessmentItem> assessments,
                            List<HistoryItem> history) {
}
