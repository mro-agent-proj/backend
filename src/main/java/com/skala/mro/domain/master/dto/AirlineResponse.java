package com.skala.mro.domain.master.dto;

import com.skala.mro.domain.master.entity.Airline;

public record AirlineResponse(String code, String name) {

    public static AirlineResponse from(Airline airline) {
        return new AirlineResponse(airline.getCode(), airline.getName());
    }
}
