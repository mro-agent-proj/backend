package com.skala.mro.domain.assignment.repository;

import com.skala.mro.domain.assignment.entity.Assignment;
import com.skala.mro.domain.assignment.entity.AssignmentStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Page<Assignment> findByDepartmentCode(String departmentCode, Pageable pageable);

    Page<Assignment> findByDepartmentCodeAndStatus(String departmentCode, AssignmentStatus status, Pageable pageable);
}
