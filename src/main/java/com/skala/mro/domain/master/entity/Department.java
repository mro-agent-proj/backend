package com.skala.mro.domain.master.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "departments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

    @Id
    private String code;

    private String name;

    /** 쉼표로 구분된 ATA 챕터 목록. 예: "29,32" */
    private String ataChapters;

    private boolean active;

    public List<String> ataChapterList() {
        return Arrays.stream(ataChapters.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
