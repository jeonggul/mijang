package com.example.mijang.dividend.batch;

import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.dividend.service.DividendEstimateService;
import com.example.mijang.dividend.service.StockDividendSyncService;
import com.example.mijang.user.service.NotificationProducerService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 08:00 KST에 배당 수집·예상 생성·알림 생성을 순서대로 돌리는 배치다. PROFIT-12 · INFO-06 · NOTI-04. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
public class StockDividendSyncScheduler {

    private final StockDividendSyncService syncService;
    private final DividendEstimateService estimateService;
    private final NotificationProducerService notificationProducerService;
    private final BatchLogWriter batchLogWriter;

    /** 배당 수집·예상 생성·알림 생성을 단계별 로그로 실행한다. */
    @Scheduled(cron = "${mijang.batch.stock-dividend-cron:0 0 8 * * *}", zone = "Asia/Seoul")
    public void run() {
        batchLogWriter.run("배당 수집", syncService::syncHeldSymbols);
        batchLogWriter.run("예상 배당 생성", estimateService::produceLatest);
        batchLogWriter.run("배당 알림 생성", () -> notificationProducerService.produceDividend(
                LocalDate.now(TradingClock.SERVICE_ZONE)));
    }
}
