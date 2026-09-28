package com.example.mijang.stock.batch;

import com.example.mijang.stock.service.StockKoreanNameSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 하루 한 번 Wikidata 에서 한글 종목명을 받아 채우는 배치다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StockKoreanNameSyncScheduler {

    private final StockKoreanNameSyncService syncService;

    /** 종목 마스터 동기화(21:00) 뒤에 돌아 새 종목에도 한글명을 붙인다. */
    @Scheduled(cron = "0 20 21 * * MON-FRI", zone = "Asia/Seoul")
    public void run() {
        try {
            syncService.syncAll();
        } catch (RuntimeException e) {
            log.error("[한글명] 동기화 실패 — 기존 이름은 그대로 남는다", e);
        }
    }
}
