package com.skala.mro.domain.auth.controller;

import com.skala.mro.domain.auth.dto.LoginRequest;
import com.skala.mro.domain.auth.dto.LoginResponse;
import com.skala.mro.domain.auth.dto.SignupRequest;
import com.skala.mro.domain.auth.dto.UserResponse;
import com.skala.mro.domain.auth.service.AuthService;
import com.skala.mro.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** 201 + 로그인 응답(가입 즉시 로그인). 이메일 중복이면 409 DUPLICATE_EMAIL. */
    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return UserResponse.from(user);
    }
}
