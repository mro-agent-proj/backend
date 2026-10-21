package com.skala.mro.domain.master.service;

import com.skala.mro.domain.master.dto.DepartmentResponse;
import com.skala.mro.domain.master.repository.DepartmentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public List<DepartmentResponse> listActive() {
        return departmentRepository.findAllByActiveTrueOrderByCode().stream().map(DepartmentResponse::from).toList();
    }
}
