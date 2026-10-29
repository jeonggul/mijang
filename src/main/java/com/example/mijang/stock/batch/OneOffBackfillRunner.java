package com.example.mijang.stock.batch;

import com.example.mijang.stock.service.DailyPriceCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 일봉 공백 백필용 일회성 러너. {@code --mijang.backfill-days=N} 을 줄 때만 뜬다.
 *
 * <p>서버가 장기간 꺼져 있어 {@link DailyPriceCollectScheduler} 의 겹침(5일)으로
 * 메울 수 없는 구멍이 생겼을 때, 기동 한 번으로 N일 치를 받아 채운다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mijang.backfill-days")
@RequiredArgsConstructor
public class OneOffBackfillRunner implements ApplicationRunner {

    private final DailyPriceCollectService dailyPriceCollectService;

    @Value("${mijang.backfill-days}")
    private int days;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[백필] 일봉 {}일 치 수집 시작", days);
        int saved = dailyPriceCollectService.collectRecent(days);
        log.info("[백필] 완료 — {}건 저장. 앱을 내려도 된다", saved);
    }
}
