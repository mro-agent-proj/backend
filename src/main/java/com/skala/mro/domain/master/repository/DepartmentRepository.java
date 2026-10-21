package com.skala.mro.domain.master.repository;

import com.skala.mro.domain.master.entity.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, String> {

    List<Department> findAllByActiveTrueOrderByCode();

    boolean existsByCodeAndActiveTrue(String code);
}
