package com.skala.mro.domain.auth.dto;

import com.skala.mro.global.security.AuthUser;
import java.util.UUID;

public record UserResponse(UUID id, String email, String name, String role, String departmentCode,
                           String airlineCode) {

    public static UserResponse from(AuthUser user) {
        return new UserResponse(user.id(), user.email(), user.name(), user.role().name(),
                user.departmentCode(), user.airlineCode());
    }
}
