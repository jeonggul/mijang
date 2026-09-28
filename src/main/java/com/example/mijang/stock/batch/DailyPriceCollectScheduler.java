package com.example.mijang.stock.batch;

import com.example.mijang.stock.service.DailyPriceCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 미국 장 마감 후 하루 1회 일봉을 수집하는 배치다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DailyPriceCollectScheduler {

    /** 재실행에 대비해 며칠 겹쳐 받는다. */
    private static final int OVERLAP_DAYS = 5;

    private final DailyPriceCollectService dailyPriceCollectService;

    /** 최근 며칠치 일봉을 겹쳐 수집한다. */
    @Scheduled(cron = "0 0 7 * * TUE-SAT", zone = "Asia/Seoul")
    public void run() {
        dailyPriceCollectService.collectRecent(OVERLAP_DAYS);
    }
}
