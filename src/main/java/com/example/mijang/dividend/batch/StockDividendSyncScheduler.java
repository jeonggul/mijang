/*
 * StockDividendSyncScheduler — 배당 수집·예상 생성 배치
 *
 * 이 파일이 하는 일
 *   매일 아침 보유 종목의 배당 이벤트를 받아 오고, 이어서 배당락일이 지난
 *   이벤트로 예상 배당(PROFIT-12)을 만든다. 실행 여부는 배치 상태 화면에 남는다.
 */
package com.example.mijang.dividend.batch;

import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.dividend.service.DividendEstimateService;
import com.example.mijang.dividend.service.StockDividendSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 배당 수집·예상 생성 배치. PROFIT-12 · INFO-06
 *
 * <p>08:00 KST — 일봉 수집(07:00)·알림 생성(07:30) 뒤다. 배당 공시는 거래일과
 * 무관하게 나오므로 휴장일을 거르지 않고 매일 돈다.
 *
 * <p>수집이 실패하면 생성은 지난 수집분으로 돈다 — 두 단계를 한 로그로 묶으면
 * 수집 실패가 생성까지 안 돈 것처럼 남아서, 따로 기록한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
public class StockDividendSyncScheduler {

    private final StockDividendSyncService syncService;
    private final DividendEstimateService estimateService;
    private final BatchLogWriter batchLogWriter;

    @Scheduled(cron = "${mijang.batch.stock-dividend-cron:0 0 8 * * *}", zone = "Asia/Seoul")
    public void run() {
        batchLogWriter.run("배당 수집", syncService::syncHeldSymbols);
        batchLogWriter.run("예상 배당 생성", estimateService::produceLatest);
    }
}
