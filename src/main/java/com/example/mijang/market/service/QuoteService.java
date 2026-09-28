package com.example.mijang.market.service;

import com.example.mijang.market.cache.QuoteCacheService;
import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.admin.service.AdminSettingService;
import com.example.mijang.market.dto.QuoteResponse;
import com.example.mijang.market.domain.MarketSession;
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

/** 현재가를 조회한다. 실시간 값이 없으면 마지막 일봉 종가를 live=false 로 준다. */
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteCacheService cache;
    private final SubscriptionPoolManager pool;
    private final DailyPriceMapper dailyPriceMapper;
    private final com.example.mijang.market.service.MarketCalendarService calendar;
    private final AdminSettingService settingService;

    /** 한 종목의 현재가를 반환한다. 장이 닫혀 있으면 실시간 표시를 내려서 준다. */
    @Transactional(readOnly = true)
    public Optional<QuoteResponse> quote(String symbol) {
        return quote(symbol, calendar.currentSession());
    }

    /** 여러 종목의 현재가를 반환한다. 장 구간은 여기서 한 번만 구한다. */
    @Transactional(readOnly = true)
    public List<QuoteResponse> quotes(List<String> symbols) {
        MarketSession session = calendar.currentSession();
        return symbols.stream()
                .map(s -> quote(s, session))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<QuoteResponse> quote(String symbol, MarketSession session) {
        String key = normalize(symbol);
        Optional<QuoteResponse> cached = cache.get(key);
        if (cached.isPresent()) {
            return cached.map(quote -> forSession(quote, session));
        }
        return fromDailyClose(key);
    }

    /** 정규장 IEX 값이 프리·애프터마켓에서 계속 "실시간"으로 보이지 않게 표시를 내린다. */
    private QuoteResponse forSession(QuoteResponse quote, MarketSession session) {
        // 운영 설정에서 실시간 공급을 끄면 값은 그대로 주되 실시간 표시만 내린다.
        if (!settingService.isOn(AdminSettingKey.QUOTE_LIVE_ENABLED)) {
            return demoteToClosed(quote);
        }
        if (!session.live() || (session != MarketSession.REGULAR && !quote.delayed())) {
            return demoteToClosed(quote);
        }
        return quote;
    }

    /** 실시간 표시를 내린다. 값과 시각은 그대로 둔다. */
    private static QuoteResponse demoteToClosed(QuoteResponse quote) {
        return quote.live()
                ? new QuoteResponse(quote.symbol(), quote.price(), quote.at(), false, false)
                : quote;
    }

    /** 이 종목이 지금 실시간으로 들어오고 있는지 반환한다. */
    public boolean isLive(String symbol) {
        return pool.contains(normalize(symbol));
    }

    /** 일봉 종가를 현재가 자리에 놓는다. 시각은 그날 자정(UTC)으로 만든다. */
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
