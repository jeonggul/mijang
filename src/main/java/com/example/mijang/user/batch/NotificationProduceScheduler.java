/*
 * NotificationProduceScheduler — 알림 생성 배치
 *
 * 이 파일이 하는 일
 *   일봉 수집(07:00)이 끝난 뒤 그날 치 알림을 만든다. 실행 여부는 배치 상태 화면에 남는다.
 */
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

/**
 * 알림 생성 배치. NOTI-01·02 · 4.5 점검 2.2
 *
 * <p>화·토 07:30 KST — 일봉 수집(07:00, {@code DailyPriceCollectScheduler})이 끝난 뒤다.
 * 순서가 뒤집히면 아직 없는 일봉을 보고 "조건에 안 걸렸다" 로 조용히 지나간다.
 *
 * <p>판정일은 배치 시각의 ET 거래일이다. 07:30 KST 는 ET 로 전날 저녁이라,
 * {@code tradeDate(now)} 가 곧 방금 마감한 거래일이 된다.
 */
@Component
@RequiredArgsConstructor
public class NotificationProduceScheduler {

    private final NotificationProducerService producerService;
    private final BatchLogWriter batchLogWriter;
    private final TradingClock tradingClock;
    private final MarketCalendar marketCalendar;

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
