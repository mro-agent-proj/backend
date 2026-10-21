package com.skala.mro.domain.request.entity;

import java.util.UUID;

/** 상태를 바꾼 주체. 상태 이력과 배정 기록에 남습니다. */
public record Actor(ActorType type, UUID userId) {

    public static final Actor AI = new Actor(ActorType.AI, null);
    public static final Actor SYSTEM = new Actor(ActorType.SYSTEM, null);

    public static Actor user(UUID userId) {
        return new Actor(ActorType.USER, userId);
    }

    public enum ActorType {
        USER, AI, SYSTEM
    }
}
