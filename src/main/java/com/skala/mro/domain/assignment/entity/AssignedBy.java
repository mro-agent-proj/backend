package com.skala.mro.domain.assignment.entity;

public enum AssignedBy {
    /** 에이전트 판단을 Spring이 검증 후 배정 */
    AI,
    /** 사람 검토에서 어드민이 수동 배정 */
    ADMIN
}
