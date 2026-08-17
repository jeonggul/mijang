/*
 * QuoteService — 현재가를 구해 주는 곳
 *
 * 이 파일이 하는 일
 *   실시간 값이 있으면 그것을, 없으면 마지막 일봉 종가를 준다.
 *   비어 있는 화면보다 조금 지난 값이라도 보여 주는 편이 낫고,
 *   그것이 실시간인지 아닌지는 응답에 표시해 사용자가 알 수 있게 한다.
 */
package com.example.mijang.market.service;

import com.example.mijang.market.cache.QuoteCacheService;
import com.example.mijang.market.dto.QuoteResponse;
import com.example.mijang.market.pool.SubscriptionPoolManager;
import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.mapper.DailyPriceMapper;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재가 조회. 개발명세서(API) PRICE-01·WATCH-02
 *
 * <p>실시간이 있으면 그것을, 없으면 <b>마지막 일봉 종가</b>를 준다(2.8).
 * 화면이 비어 있는 것보다 낫고, 실시간 여부는 {@code live} 로 알려 준다.
 */
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteCacheService cache;
    private final SubscriptionPoolManager pool;
    private final DailyPriceMapper dailyPriceMapper;
    private final com.example.mijang.market.service.MarketCalendarService calendar;

    /**
     * 한 종목의 현재가.
     *
     * <p>캐시에 없으면 일봉을 읽어 {@code live=false} 로 만들어 준다.
     * 일봉조차 없으면 비어 있음 — 수집 전 종목이다.
     *
     * <p>장이 닫혀 있으면 캐시에 남은 값이라도 <b>실시간 표시를 내려서</b> 준다.
     * 마지막 체결은 금요일 것인데 배지가 "실시간" 이면 사용자는 지금 거래되고 있는
     * 값으로 읽는다. 마감 배치가 꺼져 있어도 맞아야 해서 내줄 때 판단한다.
     */
    @Transactional(readOnly = true)
    public Optional<QuoteResponse> quote(String symbol) {
        return quote(symbol, marketOpen());
    }

    /**
     * 여러 종목의 현재가. 관심종목 목록이 쓴다.
     *
     * <p>종목마다 따로 부르지 않고 한 번에 처리한다.
     *
     * <p><b>장 구간은 여기서 한 번만 구한다.</b> 종목마다 물으면 달력 질의가 종목 수만큼
     * 늘어난다 — 30종목이면 60번이다. 한 번의 요청 안에서 구간이 바뀔 일도 없다.
     */
    @Transactional(readOnly = true)
    public List<QuoteResponse> quotes(List<String> symbols) {
        boolean open = marketOpen();
        return symbols.stream()
                .map(s -> quote(s, open))
                .flatMap(Optional::stream)
                .toList();
    }

    /** 지금 값이 움직이는 시간인가. 프리마켓·정규장·시간외면 참이다 */
    private boolean marketOpen() {
        return calendar.currentSession().live();
    }

    private Optional<QuoteResponse> quote(String symbol, boolean marketOpen) {
        String key = normalize(symbol);
        Optional<QuoteResponse> cached = cache.get(key);
        if (cached.isPresent()) {
            return marketOpen ? cached : cached.map(QuoteService::demoteToClosed);
        }
        return fromDailyClose(key);
    }

    /**
     * 실시간 표시를 내린다. 값과 시각은 그대로 둔다.
     *
     * <p>마지막 체결은 금요일 것인데 배지가 "실시간" 이면 사용자는 지금 거래되고 있는
     * 값으로 읽는다. 마감 배치가 꺼져 있어도 맞아야 해서 내줄 때 판단한다.
     */
    private static QuoteResponse demoteToClosed(QuoteResponse quote) {
        return quote.live()
                ? new QuoteResponse(quote.symbol(), quote.price(), quote.at(), false, false)
                : quote;
    }

    /** 이 종목이 지금 실시간으로 들어오고 있는지. 화면 배지에 쓴다. */
    public boolean isLive(String symbol) {
        return pool.contains(normalize(symbol));
    }

    /**
     * 일봉 종가를 현재가 자리에 놓는다.
     *
     * <p>거래일에는 시각이 없으므로 그날 자정(UTC)으로 만든다. 화면은 날짜만 쓴다.
     */
    private Optional<QuoteResponse> fromDailyClose(String symbol) {
        CandleResponse latest = dailyPriceMapper.findLatest(symbol);
        if (latest == null) {
            return Optional.empty();
        }
        return Optional.of(new QuoteResponse(
                symbol, latest.close(),
                latest.tradeDate().atStartOfDay().toInstant(ZoneOffset.UTC),
                false, false));
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
