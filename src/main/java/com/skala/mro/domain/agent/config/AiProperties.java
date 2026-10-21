package com.skala.mro.domain.agent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param mode      rabbit: 실제 AI 서버가 실행 요청 큐를 소비 / mock: 앱 안의 MockAiServer가 소비
 * @param timeout   실행 요청 큐의 메시지 TTL이자 처리 시간 한도. 넘으면 사람 검토(SYSTEM_ERROR)
 * @param mockDelay MockAiServer가 단계마다 쉬는 시간
 */
@ConfigurationProperties("mro.ai")
public record AiProperties(String mode, Duration timeout, Duration mockDelay) {
}
