package com.skala.mro.domain.master.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "airlines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Airline {

    @Id
    private String code;

    private String name;
}
