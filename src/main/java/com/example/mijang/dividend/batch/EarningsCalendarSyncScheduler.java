package com.example.mijang.dividend.batch;

import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.dividend.service.EarningsCalendarSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실적 캘린더를 매일 08:10 KST에 수집하는 배치다. INFO-05. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
public class EarningsCalendarSyncScheduler {

    private final EarningsCalendarSyncService syncService;
    private final BatchLogWriter batchLogWriter;

    /** 실적 캘린더 수집을 실행한다. */
    @Scheduled(cron = "${mijang.batch.earnings-cron:0 10 8 * * *}", zone = "Asia/Seoul")
    public void run() {
        batchLogWriter.run("실적 캘린더 수집", syncService::syncAll);
    }
}
