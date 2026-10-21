package com.skala.mro.global.security;

import com.skala.mro.domain.user.entity.Role;
import com.skala.mro.domain.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** 인증된 사용자. 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser}로 받습니다. */
public record AuthUser(UUID id, String email, String name, Role role, String departmentCode, String airlineCode) {

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                user.getDepartmentCode(), user.getAirlineCode());
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
