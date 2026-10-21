package com.skala.mro.domain.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 계정은 시드(SeedLoader)로만 만듭니다. 가입 API는 범위 밖입니다. */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    private UUID id;

    private String email;

    private String name;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String departmentCode;

    private String airlineCode;

    private boolean active;

    private Instant createdAt;
}
