package com.skala.mro.global.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * /actuator/health의 ragData 항목. 복원 전이면 UNKNOWN입니다.
 * DOWN으로 하지 않는 이유: 전체 헬스가 DOWN이 되면 compose의 rag-restore가 백엔드를 기다리다 영영 시작하지 못합니다.
 */
@Component("ragData")
@RequiredArgsConstructor
public class RagDataHealthIndicator implements HealthIndicator {

    private final RagDataStatus status;

    @Override
    public Health health() {
        RagDataStatus.Snapshot snapshot = status.check();
        return Health.status(snapshot.ready() ? Status.UP : Status.UNKNOWN)
                .withDetail("release_key", snapshot.releaseKey() == null ? "none" : snapshot.releaseKey())
                .withDetail("sdr_cases", snapshot.sdrCases())
                .build();
    }
}
