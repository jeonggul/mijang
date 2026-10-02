package com.example.mijang.portfolio.batch;

import com.example.mijang.portfolio.service.SnapshotService;
import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.common.time.MarketCalendar;
import com.example.mijang.common.time.TradingClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 사용자별 자산 스냅샷을 찍는 배치 스케줄러다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SnapshotBatchScheduler {

    private final SnapshotService snapshotService;
    private final BatchLogWriter batchLogWriter;
    private final MarketCalendar marketCalendar;
    private final TradingClock tradingClock;

    /** 일봉 수집(07:00) 이후에 일별 스냅샷 배치를 실행한다. */
    @Scheduled(cron = "${mijang.batch.snapshot-cron:0 0 8 * * TUE-SAT}", zone = "Asia/Seoul")
    public void run() {
        // 휴장일 "건너뜀"과 "0건 처리"를 구분하기 위해 여기서 거래일을 한 번 더 판정한다
        if (!marketCalendar.isTradingDay(tradingClock.today())) {
            batchLogWriter.skip("일별 스냅샷", "거래일이 아니다");
            return;
        }
        batchLogWriter.run("일별 스냅샷", snapshotService::createDailySnapshot);
    }
}
