/*
 * QuoteCacheService — 최신 시세를 담아 두는 곳
 *
 * 이 파일이 하는 일
 *   종목별 마지막 시세를 메모리에 들고 있는다.
 *   Redis 를 두지 않는 이유는 서버가 한 대이고 담을 값이 30종목뿐이어서다.
 *   재시작하면 비지만 문제가 없다 — 다음 체결이 오면 다시 차고,
 *   그 전에는 일봉 종가로 답한다.
 */
package com.example.mijang.market.cache;

import com.example.mijang.market.dto.QuoteResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 종목별 최신 시세 캐시. <b>메모리에만 둔다</b>(2.7).
 *
 * <p>Redis 를 들이지 않는 이유 — 서버가 한 대이고 담을 값이 30종목뿐이다.
 * 여러 대로 늘리는 시점이 곧 pub/sub 이 필요해지는 시점이고, 그것은 확장 범위다.
 *
 * <p>재시작하면 비지만 문제가 없다. 다음 체결이 오면 다시 차고, 그 전에는
 * 종가로 응답한다(2.8).
 */
@Service
public class QuoteCacheService {

    /** 티커 → 최신 시세. 여러 스레드가 동시에 읽고 쓴다. */
    private final Map<String, QuoteResponse> cache = new ConcurrentHashMap<>();

    /**
     * 실시간 체결을 넣는다. 웹소켓 수신부가 부른다.
     *
     * <p>{@code live=true} 로 넣는다 — 장중에 받은 실제 체결이라는 뜻이다.
     */
    public void putLive(String symbol, BigDecimal price, Instant at) {
        /* 더 새것만 남긴다. 그냥 덮어쓰면 시세가 뒤로 갈 수 있다 —
           피드를 갈아타는 동안 옛 지연 피드에 남아 있던 15분 전 체결이 뒤늦게 도착하면,
           그것이 방금 받은 실시간 값을 밀어내고 "실시간" 딱지까지 달고 나간다.
           putDelayed 가 쓰는 것과 같은 규칙이다 */
        cache.merge(symbol, new QuoteResponse(symbol, price, at, true, false),
                (old, fresh) -> old.at().isAfter(fresh.at()) ? old : fresh);
    }

    /**
     * 종가를 넣는다. 장 마감 시 실시간 값을 대체한다(2.5).
     *
     * <p>{@code live=false} 라 화면이 "장 마감"으로 표시한다.
     */
    public void putClose(String symbol, BigDecimal close, Instant at) {
        cache.put(symbol, new QuoteResponse(symbol, close, at, false, false));
    }

    /** 캐시된 시세. 없으면 비어 있음 — 호출부가 일봉 종가로 대신한다. */
    /**
     * 15분 지연 체결을 넣는다.
     *
     * <p>장 시간 밖에는 IEX 에 체결이 거의 없어 웹소켓이 아무것도 밀어 주지 못한다.
     * 실측으로 애프터마켓 10분에 0건이었다. 그때는 이쪽이 유일하게 움직이는 값이다.
     *
     * <p>스트림으로 온 값이 더 새것이면 덮어쓰지 않는다 — 지연값이 실시간을 밀어내면
     * 시세가 뒤로 간다.
     */
    public void putDelayed(String symbol, BigDecimal price, Instant at) {
        cache.merge(symbol, new QuoteResponse(symbol, price, at, true, true),
                (old, fresh) -> old.at().isAfter(fresh.at()) ? old : fresh);
    }

    public Optional<QuoteResponse> get(String symbol) {
        return Optional.ofNullable(cache.get(symbol));
    }

    /** 장 마감 시 실시간 표시를 걷어낸다. 값은 두고 플래그만 내린다. */
    public void markAllClosed() {
        cache.replaceAll((symbol, quote) ->
                new QuoteResponse(quote.symbol(), quote.price(), quote.at(), false, false));
    }

    /** 구독에서 빠진 종목을 지운다. 오래된 값이 실시간인 척 남지 않게 한다. */
    public void evict(String symbol) {
        cache.remove(symbol);
    }
}
