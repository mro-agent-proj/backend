package com.skala.mro.domain.staffing.service;

import com.skala.mro.domain.master.repository.DepartmentRepository;
import com.skala.mro.domain.staffing.dto.StaffingResponse;
import com.skala.mro.domain.staffing.dto.StaffingResponse.DepartmentStaffing;
import com.skala.mro.domain.staffing.dto.StaffingResponse.Member;
import com.skala.mro.domain.staffing.entity.Technician;
import com.skala.mro.domain.staffing.repository.TechnicianRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffingService {

    private final DepartmentRepository departmentRepository;
    private final TechnicianRepository technicianRepository;

    /** 활성 부서마다 한 칸. 명단이 없는 부서도 0명으로 보여줍니다. */
    public StaffingResponse summary() {
        Map<String, List<Technician>> byDepartment = technicianRepository.findAllByOrderById().stream()
                .collect(Collectors.groupingBy(Technician::getDepartmentCode));
        List<DepartmentStaffing> departments = departmentRepository.findAllByActiveTrueOrderByCode().stream()
                .map(d -> {
                    List<Technician> members = byDepartment.getOrDefault(d.getCode(), List.of());
                    return new DepartmentStaffing(d.getCode(), d.getName(), members.size(),
                            count(members, Technician::isOnDuty), count(members, Technician::isAvailable),
                            members.stream().map(Member::from).toList());
                })
                .toList();
        return new StaffingResponse(
                departments.stream().mapToInt(DepartmentStaffing::total).sum(),
                departments.stream().mapToInt(DepartmentStaffing::onDuty).sum(),
                departments.stream().mapToInt(DepartmentStaffing::available).sum(),
                departments);
    }

    private static int count(List<Technician> members, Predicate<Technician> condition) {
        return (int) members.stream().filter(condition).count();
    }
}
