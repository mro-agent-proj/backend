-- 앱 테이블은 public 스키마(Spring 소유). AI 서버 체크포인트와 RAG 벡터는 각자 스키마를 씁니다.
create schema if not exists langgraph;
create schema if not exists rag;

create table airlines (
    code varchar(3) primary key,
    name text not null
);

create table departments (
    code         varchar(8) primary key,
    name         text    not null,
    ata_chapters text    not null default '',
    active       boolean not null default true
);

create table aircraft (
    tail_number    varchar(16) primary key,
    msn            text,
    aircraft_model text       not null,
    airline_code   varchar(3) not null references airlines (code),
    active         boolean    not null default true
);

create table maintenance_history (
    id              varchar(16) primary key,
    tail_number     varchar(16) not null references aircraft (tail_number),
    reported_at     timestamptz not null,
    symptom_text    text        not null,
    ata_chapter     varchar(2)  not null,
    action_taken    text,
    department_code varchar(8) references departments (code),
    closed_at       timestamptz
);
create index idx_maintenance_history_tail on maintenance_history (tail_number, reported_at desc);

create table users (
    id              uuid primary key,
    email           text        not null unique,
    name            text        not null,
    password_hash   text        not null,
    role            varchar(16) not null check (role in ('REPORTER', 'MAINTENANCE', 'ADMIN')),
    department_code varchar(8) references departments (code),
    airline_code    varchar(3) references airlines (code),
    active          boolean     not null default true,
    created_at      timestamptz not null default now(),
    constraint chk_users_department check ((role = 'MAINTENANCE') = (department_code is not null)),
    constraint chk_users_airline check (role <> 'REPORTER' or airline_code is not null)
);

create table repair_requests (
    id                  uuid primary key,
    reporter_id         uuid        not null references users (id),
    tail_number         varchar(16) not null references aircraft (tail_number),
    aircraft_model      text        not null,
    airline_code        varchar(3)  not null references airlines (code),
    symptom_text        text        not null,
    status              varchar(20) not null,
    review_reason       varchar(24),
    assigned_department varchar(8) references departments (code),
    summary             text,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    version             bigint      not null default 0
);
create index idx_repair_requests_reporter on repair_requests (reporter_id, created_at desc);
create index idx_repair_requests_status on repair_requests (status);
create index idx_repair_requests_department on repair_requests (assigned_department);

create table request_status_history (
    id            bigserial primary key,
    request_id    uuid        not null references repair_requests (id),
    from_status   varchar(20),
    to_status     varchar(20) not null,
    review_reason varchar(24),
    actor_type    varchar(10) not null,
    actor_id      uuid,
    note          text,
    created_at    timestamptz not null default now()
);
create index idx_request_status_history_request on request_status_history (request_id, id);

create table agent_events (
    event_id    uuid primary key,
    request_id  uuid        not null references repair_requests (id),
    type        varchar(20) not null,
    occurred_at timestamptz,
    payload     jsonb,
    received_at timestamptz not null default now()
);
create index idx_agent_events_request on agent_events (request_id);

create table agent_assessments (
    id         bigserial primary key,
    request_id uuid        not null references repair_requests (id),
    stage      varchar(20) not null,
    attempt    int         not null,
    output     jsonb,
    created_at timestamptz not null default now()
);
create index idx_agent_assessments_request on agent_assessments (request_id, id);

create table assignments (
    id                  uuid primary key,
    request_id          uuid        not null references repair_requests (id),
    department_code     varchar(8)  not null references departments (code),
    status              varchar(12) not null,
    assigned_by         varchar(8)  not null,
    assigned_by_user_id uuid references users (id),
    responded_by        uuid references users (id),
    reject_reason       text,
    created_at          timestamptz not null,
    responded_at        timestamptz,
    version             bigint      not null default 0
);
-- 요청 하나에 응답 대기 중인 배정은 최대 1건
create unique index uq_assignments_pending on assignments (request_id) where status = 'PENDING';
create index idx_assignments_department on assignments (department_code, status, created_at desc);

create table human_reviews (
    id              uuid primary key,
    request_id      uuid        not null references repair_requests (id),
    reason          varchar(24) not null,
    status          varchar(10) not null,
    note            text,
    resolution      varchar(10),
    department_code varchar(8) references departments (code),
    resolution_note text,
    resolved_by     uuid references users (id),
    created_at      timestamptz not null,
    resolved_at     timestamptz,
    version         bigint      not null default 0
);
-- 요청 하나에 열린 사람 검토는 최대 1건
create unique index uq_human_reviews_open on human_reviews (request_id) where status = 'OPEN';
create index idx_human_reviews_status on human_reviews (status, created_at);
