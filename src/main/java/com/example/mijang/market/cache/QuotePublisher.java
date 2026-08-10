package com.example.mijang.market.cache;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 시세 분배. 같은 종목을 여러 사용자가 봐도 벤더 구독은 1건만 두고 여기서 나눠 준다.
 *
 * <p>개발명세서(MVC) · 실시간 시세 · cache
 * <p>TODO: Redis pub/sub 으로 교체한다.
 */
@Component
public class QuotePublisher {

    public void publish(String symbol, BigDecimal price) {
        throw new UnsupportedOperationException("TODO: 구독 중인 SseEmitter 로 분배");
    }
}
