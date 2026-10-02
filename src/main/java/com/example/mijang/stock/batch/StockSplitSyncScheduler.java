/*
 * StockSplitSyncScheduler — 분할 받아 오기
 *
 * 이 파일이 하는 일
 *   하루 한 번 보유 종목의 분할 이벤트를 받아 온다.
 *
 *   왜 장 열기 전인가
 *     분할 기준일 아침부터 시세가 조정된 값으로 들어온다. 그 전에 표를 채워 둬야
 *     보유 수량 보정과 시세가 같은 기준을 본다. 하루 늦으면 그날 하루 동안
 *     평가금액이 배수만큼 어긋난 채로 보인다.
 *
 *   배당 수집(08:00)보다 앞에 둔다. 둘 다 같은 벤더 경로를 쓰는데, 분할이 먼저
 *   반영돼야 배당 추정이 맞는 수량 위에서 돈다.
 */
package com.example.mijang.stock.batch;

import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.stock.service.StockSplitSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 분할 수집 배치. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
public class StockSplitSyncScheduler {

    private final StockSplitSyncService syncService;
    private final BatchLogWriter batchLogWriter;

    @Scheduled(cron = "${mijang.batch.stock-split-cron:0 30 7 * * *}", zone = "Asia/Seoul")
    public void run() {
        batchLogWriter.run("분할 수집", syncService::syncHeldSymbols);
    }
}
