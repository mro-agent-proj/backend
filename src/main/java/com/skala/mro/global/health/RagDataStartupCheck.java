package com.skala.mro.global.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 시작할 때 RAG 데이터가 없으면 경고를 남깁니다 (RESTORE.md: 미준비 상태를 로그에 남김). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagDataStartupCheck {

    private final RagDataStatus status;

    @EventListener(ApplicationReadyEvent.class)
    public void logStatus() {
        RagDataStatus.Snapshot snapshot = status.check();
        if (snapshot.ready()) {
            log.info("RAG 데이터 준비됨: release={}, SDR {}건", snapshot.releaseKey(), snapshot.sdrCases());
        } else {
            log.warn("RAG 데이터가 아직 없습니다. data/rag/에 전달본을 두고 scripts/rag-restore.sh를 실행하세요 "
                    + "(docker compose up이면 rag-restore 서비스가 자동 실행). 그 전까지 rag-mcp 검색 결과는 비어 있습니다.");
        }
        if (snapshot.techDocuments() > 0) {
            log.info("R04 기술자료 준비됨: 문서 {}편", snapshot.techDocuments());
        } else {
            log.warn("R04 기술자료가 아직 없습니다. data/rag/에 경님 전달본을 두고 scripts/tech-restore.sh를 실행하세요.");
        }
    }
}
