package com.example.mijang.stock.batch;

import com.example.mijang.stock.service.StockSyncService;
import com.example.mijang.admin.service.BatchLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 미국 장 개장 전 하루 1회 종목 마스터를 동기화하는 배치다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StockMasterSyncScheduler {

    private final StockSyncService stockSyncService;
    private final BatchLogWriter batchLogWriter;

    /** 평일 21:00 에 종목 마스터를 동기화한다. */
    @Scheduled(cron = "0 0 21 * * MON-FRI", zone = "Asia/Seoul")
    public void run() {
        batchLogWriter.run("종목 마스터 동기화", stockSyncService::syncAll);
    }
}
