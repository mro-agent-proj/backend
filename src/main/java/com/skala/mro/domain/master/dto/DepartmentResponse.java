package com.skala.mro.domain.master.dto;

import com.skala.mro.domain.master.entity.Department;
import java.util.List;

public record DepartmentResponse(String code, String name, List<String> ataChapters) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.getCode(), department.getName(), department.ataChapterList());
    }
}
