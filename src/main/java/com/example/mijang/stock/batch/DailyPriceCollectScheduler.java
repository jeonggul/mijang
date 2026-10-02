package com.example.mijang.stock.batch;

import com.example.mijang.stock.service.DailyPriceCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일봉 수집. 미국 장 마감 후 하루 1회.
 *
 * <p>한국시간 07:00 은 미국 정규장 마감(06:00, 서머타임 기준) 한 시간 뒤다.
 * 마감 직후에 부르면 그날 봉이 아직 확정되지 않아 값이 바뀔 수 있다.
 *
 * <p>최근 5일치를 받는다. 하루치만 받으면 배치가 한 번 실패했을 때 그날이 영영 빈다.
 * 겹치는 날은 upsert 라 덮어써도 문제없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DailyPriceCollectScheduler {

    /** 재실행에 대비해 며칠 겹쳐 받는다. */
    private static final int OVERLAP_DAYS = 5;

    private final DailyPriceCollectService dailyPriceCollectService;

    @Scheduled(cron = "0 0 7 * * TUE-SAT", zone = "Asia/Seoul")
    public void run() {
        dailyPriceCollectService.collectRecent(OVERLAP_DAYS);
    }
}
