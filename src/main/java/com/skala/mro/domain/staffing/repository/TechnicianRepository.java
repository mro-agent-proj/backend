package com.skala.mro.domain.staffing.repository;

import com.skala.mro.domain.staffing.entity.Technician;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicianRepository extends JpaRepository<Technician, String> {

    List<Technician> findAllByOrderById();
}
