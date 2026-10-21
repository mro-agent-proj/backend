package com.skala.mro.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 신고자(REPORTER)만 직접 가입합니다. 정비팀·어드민 계정은 시드로 만듭니다. */
public record SignupRequest(@NotBlank @Email String email,
                            @NotBlank @Size(min = 8, max = 72) String password,
                            @NotBlank @Size(max = 50) String name,
                            @NotBlank String airlineCode) {
}
