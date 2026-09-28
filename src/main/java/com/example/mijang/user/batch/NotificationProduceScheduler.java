package com.example.mijang.user.batch;

import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.common.time.MarketCalendar;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.user.service.NotificationProducerService;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 일봉 수집(07:00 KST)이 끝난 화·토 07:30 KST에 그날 치 알림을 생성하는 배치다 — 순서가 뒤집히면 일봉이 없어 조건 미달로 조용히 지나간다. */
@Component
@RequiredArgsConstructor
public class NotificationProduceScheduler {

    private final NotificationProducerService producerService;
    private final BatchLogWriter batchLogWriter;
    private final TradingClock tradingClock;
    private final MarketCalendar marketCalendar;

    /** 판정 거래일의 알림을 생성한다. */
    @Scheduled(cron = "${mijang.batch.notification-cron:0 30 7 * * TUE-SAT}", zone = "Asia/Seoul")
    public void run() {
        LocalDate tradeDate = tradingClock.tradeDate(Instant.now());
        if (!marketCalendar.isTradingDay(tradeDate)) {
            /* 휴장일에 안 돈 것과 실패해서 못 돈 것은 다르다(admin 2.4) */
            batchLogWriter.skip("알림 생성", "거래일이 아니다");
            return;
        }
        batchLogWriter.run("알림 생성", () -> producerService.produce(tradeDate));
    }
}
