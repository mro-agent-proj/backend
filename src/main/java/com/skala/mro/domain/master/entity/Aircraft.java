package com.skala.mro.domain.master.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "aircraft")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Aircraft {

    @Id
    private String tailNumber;

    private String msn;

    private String aircraftModel;

    private String airlineCode;

    private boolean active;
}
