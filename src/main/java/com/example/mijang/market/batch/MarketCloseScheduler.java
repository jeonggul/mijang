package com.example.mijang.market.batch;

import com.example.mijang.market.cache.QuoteCacheService;
import com.example.mijang.market.client.AlpacaWebSocketClient;
import com.example.mijang.market.pool.SubscriptionPoolManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 시간외 거래가 끝나는 20시(ET)에 벤더 연결을 닫고 실시간 표시를 내린다. */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MarketCloseScheduler {

    private final SubscriptionPoolManager pool;
    private final QuoteCacheService cache;

    /** 벤더 수신부. 설정으로 꺼 둘 수 있어 없을 수도 있다. */
    private final ObjectProvider<AlpacaWebSocketClient> stream;

    /** 장 마감을 정리한다. 구독을 비우고 연결을 닫고 실시간 표시를 내린다. */
    @Scheduled(cron = "0 5 20 * * MON-FRI", zone = "America/New_York")
    public void run() {
        int watching = pool.current().size();

        /* 구독을 먼저 비워야 switchFeedIfNeeded 가 연결을 다시 붙이지 않는다 */
        pool.clear();
        stream.ifAvailable(AlpacaWebSocketClient::disconnect);
        cache.markAllClosed();
        log.info("[배치] 장 마감 정리 — 구독 비움, 연결 닫음, 실시간 표시 내림 (직전 {}종목)",
                watching);
    }
}
