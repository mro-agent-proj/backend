package com.skala.mro.domain.master.repository;

import com.skala.mro.domain.master.entity.Airline;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirlineRepository extends JpaRepository<Airline, String> {

    List<Airline> findAllByOrderByCode();
}
