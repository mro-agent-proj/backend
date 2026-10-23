package com.skala.mro.domain.staffing.dto;

import com.skala.mro.domain.staffing.entity.Technician;
import java.util.List;

/** 부서별 인원 현황. 숫자는 모두 명단(members)에서 센 값입니다. */
public record StaffingResponse(int total, int onDuty, int available, List<DepartmentStaffing> departments) {

    public record DepartmentStaffing(String code, String name, int total, int onDuty, int available,
                                     List<Member> members) {
    }

    public record Member(String id, String name, String title, String shift, boolean onDuty, boolean available,
                         String note) {

        public static Member from(Technician t) {
            return new Member(t.getId(), t.getName(), t.getTitle(), t.getShift(), t.isOnDuty(), t.isAvailable(),
                    t.getNote());
        }
    }
}
