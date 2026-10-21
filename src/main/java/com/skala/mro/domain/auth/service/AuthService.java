package com.skala.mro.domain.auth.service;

import com.skala.mro.domain.auth.dto.LoginRequest;
import com.skala.mro.domain.auth.dto.LoginResponse;
import com.skala.mro.domain.auth.dto.UserResponse;
import com.skala.mro.domain.user.entity.User;
import com.skala.mro.domain.user.repository.UserRepository;
import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.error.ErrorCode;
import com.skala.mro.global.security.AuthUser;
import com.skala.mro.global.security.JwtProvider;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        return new LoginResponse(jwtProvider.issue(user), "bearer", jwtProvider.ttlSeconds(),
                UserResponse.from(AuthUser.from(user)));
    }
}
