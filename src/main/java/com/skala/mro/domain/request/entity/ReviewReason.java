package com.skala.mro.domain.request.entity;

public enum ReviewReason {
    /** LLM3이 사람 판단이 필요하다고 판단 */
    AGENT_NEEDS_HUMAN,
    /** 재조사 한도 초과 */
    RETRY_LIMIT,
    /** 정비팀 거절 */
    TEAM_REJECTED,
    /** 배정 실행 단계의 검증 실패 (없는 부서 등) */
    VALIDATION_FAILED,
    /** AI 서버 호출 실패·예외·시간 초과 */
    SYSTEM_ERROR
}
