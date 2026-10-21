-- SDR의 JASC 코드(4자리). 같은 ATA 안에서 결함을 더 세밀하게 구분하는 데 씁니다 (시나리오 S21·S22).
alter table maintenance_history
    add column jasc_code varchar(4) check (jasc_code ~ '^[0-9]{4}$');
