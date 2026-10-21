package com.skala.mro.domain.request.entity;

import java.util.EnumSet;
import java.util.Set;

/** 인터페이스 규약 0번 섹션의 요청 상태. 전이 규칙은 canTransitionTo 한 곳에서만 정의합니다. */
public enum RequestStatus {
    QUEUED, CLASSIFYING, INVESTIGATING, REVIEWING, ASSIGNED, ACCEPTED, HUMAN_REVIEW, CLOSED;

    /** AI 서버가 처리 중인 상태. 이 상태에서만 AI 콜백이 반영됩니다. */
    public static final Set<RequestStatus> IN_PROGRESS = EnumSet.of(QUEUED, CLASSIFYING, INVESTIGATING, REVIEWING);

    public boolean isInProgress() {
        return IN_PROGRESS.contains(this);
    }

    public boolean canTransitionTo(RequestStatus to) {
        if (this == to) {
            return false;
        }
        return switch (this) {
            // 재조사 루프(REVIEWING → INVESTIGATING)와 LLM1 직행 배정(CLASSIFYING → ASSIGNED)을 모두 허용
            case QUEUED, CLASSIFYING, INVESTIGATING, REVIEWING ->
                    (to.isInProgress() && to != QUEUED) || to == ASSIGNED || to == HUMAN_REVIEW;
            case ASSIGNED -> to == ACCEPTED || to == HUMAN_REVIEW;
            case HUMAN_REVIEW -> to == ASSIGNED || to == CLOSED;
            case ACCEPTED, CLOSED -> false;
        };
    }
}
