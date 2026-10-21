package com.skala.mro.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mro.jwt")
public record JwtProperties(String secret, Duration ttl) {
}
