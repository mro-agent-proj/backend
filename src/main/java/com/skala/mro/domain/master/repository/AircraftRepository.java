package com.skala.mro.domain.master.repository;

import com.skala.mro.domain.master.entity.Aircraft;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftRepository extends JpaRepository<Aircraft, String> {
}
