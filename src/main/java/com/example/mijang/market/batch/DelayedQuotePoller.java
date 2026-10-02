package com.example.mijang.market.batch;

import com.example.mijang.market.cache.QuoteCacheService;
import com.example.mijang.market.client.AlpacaWebSocketClient;
import com.example.mijang.market.pool.SubscriptionPoolManager;
import com.example.mijang.market.stream.SseEmitterRegistry;
import com.example.mijang.stock.client.AlpacaStockClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** 구독 종목의 지연 시세를 주기적으로 받아 캐시에 넣고 화면에 뿌린다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mijang.market.stream-enabled", havingValue = "true")
public class DelayedQuotePoller {

    /** 무료 요금제에서 열리는 지연 피드. 전 거래소를 합친 값이다. */
    private static final String FEED = "delayed_sip";

    private final AlpacaStockClient alpacaClient;
    private final SubscriptionPoolManager pool;
    private final QuoteCacheService cache;
    private final SseEmitterRegistry registry;
    private final AlpacaWebSocketClient stream;

    /** 20초마다 지연 시세를 받아 뿌린다. 구독 풀이 비어 있으면 부르지 않는다. */
    @Scheduled(fixedDelay = 20_000)
    public void poll() {
        Set<String> symbols = pool.current();
        if (symbols.isEmpty()) {
            return;
        }
        /* 살아서 인증까지 끝난 스트림이 지연 피드에 붙어 있을 때만 스트림에 맡긴다 */
        if (stream.live() && stream.delayedFeed()) {
            return;
        }
        JsonNode response = alpacaClient.latestTrades(List.copyOf(symbols), FEED);
        JsonNode trades = response == null ? null : response.get("trades");
        if (trades == null || !trades.isObject()) {
            return;
        }
        int pushed = 0;
        // Jackson 3 에서는 fields() 가 아니라 properties() 다
        for (var entry : trades.properties()) {
            JsonNode price = entry.getValue().get("p");
            if (price == null || price.isNull()) {
                continue;
            }
            cache.putDelayed(entry.getKey(), new BigDecimal(price.asString()), parseAt(entry.getValue()));
            cache.get(entry.getKey()).ifPresent(registry::broadcast);
            pushed++;
        }
        log.debug("[지연 시세] {}종목 갱신", pushed);
    }

    /** 체결 시각을 파싱한다. 깨져 오면 지금으로 본다. */
    private Instant parseAt(JsonNode trade) {
        try {
            String raw = trade.path("t").asText("");
            return raw.isEmpty() ? Instant.now() : Instant.parse(raw);
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }
}
