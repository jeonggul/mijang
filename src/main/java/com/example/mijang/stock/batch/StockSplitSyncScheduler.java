package com.example.mijang.stock.batch;

import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.stock.service.StockSplitSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 하루 한 번 보유 종목의 분할 이벤트를 받아 오는 배치다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
public class StockSplitSyncScheduler {

    private final StockSplitSyncService syncService;
    private final BatchLogWriter batchLogWriter;

    /** 배당 수집(08:00)보다 먼저 돌아야 배당 추정이 맞는 수량 위에서 돈다. */
    @Scheduled(cron = "${mijang.batch.stock-split-cron:0 30 7 * * *}", zone = "Asia/Seoul")
    public void run() {
        batchLogWriter.run("분할 수집", syncService::syncHeldSymbols);
    }
}
