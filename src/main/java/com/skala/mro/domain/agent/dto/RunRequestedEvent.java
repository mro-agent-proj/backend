package com.skala.mro.domain.agent.dto;

import java.util.UUID;

/** 요청 저장 트랜잭션이 커밋된 뒤 AI 실행을 시작하기 위한 내부 이벤트. */
public record RunRequestedEvent(UUID requestId) {
}
