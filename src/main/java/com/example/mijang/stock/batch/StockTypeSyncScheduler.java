package com.example.mijang.stock.batch;

import com.example.mijang.stock.service.StockTypeSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 하루 한 번 Finnhub 에서 종목 종류를 받아 채우는 배치다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StockTypeSyncScheduler {

    private final StockTypeSyncService syncService;

    /** 종목 마스터 동기화(21:00) 뒤에 돌아 새 종목에도 종류를 붙인다. */
    @Scheduled(cron = "0 30 21 * * MON-FRI", zone = "Asia/Seoul")
    public void run() {
        try {
            syncService.syncAll();
        } catch (RuntimeException e) {
            log.error("[종목 종류] 동기화 실패 — 기존 값은 그대로 남는다", e);
        }
    }
}
