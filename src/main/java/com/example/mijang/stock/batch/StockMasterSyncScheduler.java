package com.example.mijang.stock.batch;

import com.example.mijang.stock.service.StockSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 종목 마스터 동기화. 미국 장 개장 전 하루 1회.
 *
 * <p>개장 전에 돌리는 이유 — 그날 새로 상장된 종목을 장이 열리기 전에 검색할 수 있어야 한다.
 * 한국시간 21:00 은 서머타임 기준 미국 개장(22:30) 한 시간 반 전이다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StockMasterSyncScheduler {

    private final StockSyncService stockSyncService;

    /** 평일만 돈다. 주말에는 상장 변동이 없다. */
    @Scheduled(cron = "0 0 21 * * MON-FRI", zone = "Asia/Seoul")
    public void run() {
        stockSyncService.syncAll();
    }
}
