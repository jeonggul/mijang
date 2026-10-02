package com.example.mijang.market.cache;

import com.example.mijang.market.dto.QuoteResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** 종목별 최신 시세를 메모리에만 담아 둔다. 재시작하면 비지만 다음 체결로 다시 찬다. */
@Service
public class QuoteCacheService {

    /** 티커 → 최신 시세. 여러 스레드가 동시에 읽고 쓴다. */
    private final Map<String, QuoteResponse> cache = new ConcurrentHashMap<>();

    /** 실시간 체결을 넣는다. */
    public void putLive(String symbol, BigDecimal price, Instant at) {
        /* 더 새것만 남긴다 — 그냥 덮어쓰면 늦게 도착한 옛 체결이 시세를 뒤로 돌린다 */
        cache.merge(symbol, new QuoteResponse(symbol, price, at, true, false),
                (old, fresh) -> old.at().isAfter(fresh.at()) ? old : fresh);
    }

    /** 종가를 넣는다. 장 마감 시 실시간 값을 대체한다. */
    public void putClose(String symbol, BigDecimal close, Instant at) {
        cache.put(symbol, new QuoteResponse(symbol, close, at, false, false));
    }

    /** 15분 지연 체결을 넣는다. 더 새것이 있으면 덮어쓰지 않는다. */
    public void putDelayed(String symbol, BigDecimal price, Instant at) {
        cache.merge(symbol, new QuoteResponse(symbol, price, at, true, true),
                (old, fresh) -> old.at().isAfter(fresh.at()) ? old : fresh);
    }

    /** 캐시된 시세를 반환한다. 없으면 비어 있다. */
    public Optional<QuoteResponse> get(String symbol) {
        return Optional.ofNullable(cache.get(symbol));
    }

    /** 실시간 표시를 걷어낸다. 값은 두고 플래그만 내린다. */
    public void markAllClosed() {
        cache.replaceAll((symbol, quote) ->
                new QuoteResponse(quote.symbol(), quote.price(), quote.at(), false, false));
    }

    /** 구독에서 빠진 종목을 지운다. */
    public void evict(String symbol) {
        cache.remove(symbol);
    }
}
