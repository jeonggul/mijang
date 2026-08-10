package com.example.mijang.market.batch;

import com.example.mijang.market.pool.SubscriptionPoolManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 장 마감 시 벤더 WebSocket 을 종료하고 종가 캐시로 대체한다. 화면 SR-004.
 *
 * <p>개발명세서 '실시간·배치 상세' 시트
 * <p>구현 전이라 mijang.batch.enabled=false 로 꺼 둔다. 켜면 아래 주기대로 실제로 돌기 시작한다.
 */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MarketCloseScheduler {

    private final SubscriptionPoolManager subscriptionPoolManager;

    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "America/New_York")
    public void run() {
        log.warn("[배치] MarketCloseScheduler 아직 구현 전이다");
        // subscriptionPoolManager.current();
    }
}
