/*
 * DelayedQuotePoller — 조용한 시간에 시세를 움직여 주는 곳
 *
 * 이 파일이 하는 일
 *   구독 중인 종목의 마지막 체결을 주기적으로 받아 캐시에 넣고 화면에 뿌린다.
 *
 *   왜 필요한가 — 실시간 웹소켓은 IEX 피드다. IEX 는 거래소 하나라
 *   장 시간 밖에는 체결이 거의 없다. 실측으로 애프터마켓 10분에 0건이었다.
 *   그동안 화면의 현재가는 한 자리에 멈춰 있고, 사용자는 고장으로 읽는다.
 *
 *   장 시간 밖은 스트림이 지연 피드로 갈아타 해결한다(AlpacaWebSocketClient).
 *   이 폴러가 맡는 것은 <b>정규장 중 거래가 뜸한 종목</b>이다.
 *   실측으로 JEPI 는 IEX 에서 10분에 4건뿐이라, 스트림만으로는 값이 거의 멈춰 있다.
 *   그때 15분 늦은 값이라도 채워 주면 화면이 살아 있다.
 *   늦었다는 사실은 응답에 실어 화면이 표시한다.
 */
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

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mijang.market.stream-enabled", havingValue = "true")
public class DelayedQuotePoller {

    /** 무료 요금제에서 열리는 지연 피드. 전 거래소를 합친 값이다 */
    private static final String FEED = "delayed_sip";

    private final AlpacaStockClient alpacaClient;
    private final SubscriptionPoolManager pool;
    private final QuoteCacheService cache;
    private final SseEmitterRegistry registry;
    private final AlpacaWebSocketClient stream;

    /**
     * 20초마다 한 번.
     *
     * <p>어차피 15분 지연이라 더 자주 불러도 새 값이 나오지 않는다. 다만 간격이 길면
     * 화면을 막 연 사람이 한참을 기다리게 되므로 이 정도로 둔다.
     *
     * <p>보는 사람이 없으면 부르지 않는다. 구독 풀이 비어 있다는 것이 그 뜻이다.
     */
    @Scheduled(fixedDelay = 20_000)
    public void poll() {
        Set<String> symbols = pool.current();
        if (symbols.isEmpty()) {
            return;
        }
        /* 장 시간 밖에는 스트림이 이미 지연 피드에 붙어 값을 밀어 준다. 두 번 할 일이 아니다.
           이 폴러가 필요한 때는 정규장 중 거래가 뜸한 종목이다 —
           IEX 는 실측으로 JEPI 가 10분에 4건뿐이라 그것만으로는 값이 거의 안 움직인다.

           단, "지연 피드에 붙어 있다" 는 것만 보면 안 된다. 연결이 끊겼거나 406 으로
           거부당했을 때도 값을 밀어 주는 사람이 없는데 여기서 빠져나가면 화면이 통째로
           멈춘다. 살아서 인증까지 끝난 상태일 때만 스트림에 맡긴다 */
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

    /** 시각이 깨져 오면 지금으로 본다. 값 하나 때문에 체결을 버릴 이유는 없다 */
    private Instant parseAt(JsonNode trade) {
        try {
            String raw = trade.path("t").asText("");
            return raw.isEmpty() ? Instant.now() : Instant.parse(raw);
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }
}
