/*
 * EarningsCalendarSyncScheduler — 실적 캘린더 수집 배치
 *
 * 배당 수집(StockDividendSyncScheduler)과 같은 패턴. 하루 1회 통짜 수집이라 가볍다.
 */
package com.example.mijang.dividend.batch;

import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.dividend.service.EarningsCalendarSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실적 캘린더 수집. INFO-05. 배당(08:00) 뒤 08:10 KST. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
public class EarningsCalendarSyncScheduler {

    private final EarningsCalendarSyncService syncService;
    private final BatchLogWriter batchLogWriter;

    @Scheduled(cron = "${mijang.batch.earnings-cron:0 10 8 * * *}", zone = "Asia/Seoul")
    public void run() {
        batchLogWriter.run("실적 캘린더 수집", syncService::syncAll);
    }
}
