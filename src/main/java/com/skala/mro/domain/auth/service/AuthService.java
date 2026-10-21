package com.skala.mro.domain.auth.service;

import com.skala.mro.domain.auth.dto.LoginRequest;
import com.skala.mro.domain.auth.dto.LoginResponse;
import com.skala.mro.domain.auth.dto.SignupRequest;
import com.skala.mro.domain.auth.dto.UserResponse;
import com.skala.mro.domain.master.repository.AirlineRepository;
import com.skala.mro.domain.user.entity.User;
import com.skala.mro.domain.user.repository.UserRepository;
import com.skala.mro.global.error.ApiException;
import com.skala.mro.global.error.ErrorCode;
import com.skala.mro.global.security.AuthUser;
import com.skala.mro.global.security.JwtProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final AirlineRepository airlineRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        return issue(user);
    }

    /** 신고자 계정을 만들고 바로 로그인 응답을 돌려줍니다. */
    @Transactional
    public LoginResponse signup(SignupRequest request) {
        String email = normalize(request.email());
        String airlineCode = request.airlineCode().trim().toUpperCase(Locale.ROOT);
        if (!airlineRepository.existsById(airlineCode)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "등록되지 않은 항공사입니다.",
                    List.of(Map.of("field", "airline_code", "issue", "NOT_FOUND")));
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
        try {
            User user = userRepository.saveAndFlush(User.reporter(email, request.name().trim(),
                    passwordEncoder.encode(request.password()), airlineCode));
            return issue(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일로 가입한 경우 (users.email unique)
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    private LoginResponse issue(User user) {
        return new LoginResponse(jwtProvider.issue(user), "bearer", jwtProvider.ttlSeconds(),
                UserResponse.from(AuthUser.from(user)));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
