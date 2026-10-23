-- 정비 인원 현황판(참고용). 화면 표시 전용이라 AI·MCP 조회 계정(mro_reader, mro_business_reader)에는 권한을 주지 않습니다.
-- 부서 공용 로그인 계정(users)과는 별개인 목업 명단입니다.
create table technicians (
    id              varchar(16) primary key,
    department_code varchar(8)  not null references departments (code),
    name            text        not null,
    title           text        not null,
    shift           varchar(4)  not null check (shift in ('주간', '야간')),
    on_duty         boolean     not null default false,
    available       boolean     not null default false,
    note            text,
    updated_at      timestamptz not null default now(),
    -- 출근하지 않은 사람은 작업가능일 수 없습니다.
    constraint chk_technicians_available check (on_duty or not available)
);

create index ix_technicians_department on technicians (department_code);
