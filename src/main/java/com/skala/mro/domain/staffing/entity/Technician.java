package com.skala.mro.domain.staffing.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인원 현황판용 정비사 목업 (V10). 로그인 계정이 아니며 조회만 합니다. */
@Entity
@Table(name = "technicians")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Technician {

    @Id
    private String id;

    private String departmentCode;

    private String name;

    /** 팀장 / 선임 정비사 / 정비사 */
    private String title;

    /** 주간 / 야간 */
    private String shift;

    private boolean onDuty;

    private boolean available;

    /** 작업 불가 사유. 예: 작업 중, 휴가 */
    private String note;

    private Instant updatedAt;
}
