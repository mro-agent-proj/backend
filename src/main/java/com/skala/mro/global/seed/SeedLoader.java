package com.skala.mro.global.seed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * local 프로파일에서 users 테이블이 비어 있으면 seed/*.csv를 적재합니다 (인터페이스 규약 4·5번 섹션).
 * 다시 적재하려면 DB를 비우세요: docker compose down -v
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class SeedLoader implements ApplicationRunner {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).get();

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    @Value("${mro.seed.dir}")
    private String seedDir;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        Integer users = jdbc.queryForObject("select count(*) from users", Integer.class);
        if (users != null && users > 0) {
            log.info("시드 건너뜀: users 테이블에 이미 {}건이 있습니다.", users);
            return;
        }
        Path dir = Path.of(seedDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            log.warn("시드 폴더가 없습니다: {}", dir);
            return;
        }
        log.info("시드 적재 시작: {}", dir);

        load(dir, "airlines.csv", r -> jdbc.update(
                "insert into airlines (code, name) values (?, ?)", r.get("code"), r.get("name")));
        load(dir, "departments.csv", r -> jdbc.update(
                "insert into departments (code, name, ata_chapters) values (?, ?, ?)",
                r.get("code"), r.get("name"), r.get("ata_chapters")));
        load(dir, "aircraft.csv", r -> jdbc.update(
                "insert into aircraft (tail_number, msn, aircraft_model, airline_code, active) values (?, ?, ?, ?, ?)",
                r.get("tail_number"), r.get("msn"), r.get("aircraft_model"), r.get("airline_code"),
                bool(r, "active", true)));
        load(dir, "maintenance_history.csv", r -> jdbc.update("""
                        insert into maintenance_history
                          (id, tail_number, reported_at, symptom_text, ata_chapter, jasc_code, action_taken,
                           department_code, closed_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                r.get("id"), r.get("tail_number"), timestamp(r, "reported_at"), r.get("symptom_text"),
                r.get("ata_chapter"), blankToNull(r, "jasc_code"), blankToNull(r, "action_taken"),
                blankToNull(r, "department_code"), timestamp(r, "closed_at")));
        load(dir, "users.csv", r -> jdbc.update("""
                        insert into users (id, email, name, password_hash, role, department_code, airline_code, active)
                        values (?, ?, ?, ?, ?, ?, ?, true)""",
                UUID.randomUUID(), r.get("email").toLowerCase(Locale.ROOT), r.get("name"),
                passwordEncoder.encode(r.get("password")), r.get("role"),
                blankToNull(r, "department_code"), blankToNull(r, "airline_code")));
    }

    private void load(Path dir, String fileName, Consumer<CSVRecord> insert) throws IOException {
        Path file = dir.resolve(fileName);
        if (!Files.exists(file)) {
            log.warn("시드 파일 없음: {}", fileName);
            return;
        }
        // 엑셀에서 저장한 CSV의 BOM 제거
        String content = Files.readString(file, StandardCharsets.UTF_8).replace("﻿", "");
        int count = 0;
        try (CSVParser parser = CSVParser.parse(content, FORMAT)) {
            for (CSVRecord record : parser) {
                insert.accept(record);
                count++;
            }
        }
        log.info("시드 {}: {}건", fileName, count);
    }

    private static String blankToNull(CSVRecord r, String column) {
        if (!r.isMapped(column)) {
            return null;
        }
        String value = r.get(column);
        return value == null || value.isBlank() ? null : value;
    }

    private static Timestamp timestamp(CSVRecord r, String column) {
        String value = blankToNull(r, column);
        return value == null ? null : Timestamp.from(Instant.parse(value));
    }

    private static boolean bool(CSVRecord r, String column, boolean defaultValue) {
        String value = blankToNull(r, column);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }
}
