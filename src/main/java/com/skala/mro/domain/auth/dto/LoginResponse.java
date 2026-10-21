package com.skala.mro.domain.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
