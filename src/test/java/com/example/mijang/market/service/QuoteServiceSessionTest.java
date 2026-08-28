package com.example.mijang.market.service;

import com.example.mijang.support.FixedSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.mijang.market.cache.QuoteCacheService;
import com.example.mijang.market.domain.MarketSession;
import com.example.mijang.market.dto.QuoteResponse;
import com.example.mijang.market.pool.SubscriptionPoolManager;
import com.example.mijang.stock.mapper.DailyPriceMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QuoteServiceSessionTest {

    private final QuoteCacheService cache = mock(QuoteCacheService.class);
    private final MarketCalendarService calendar = mock(MarketCalendarService.class);
    private final QuoteService service = new QuoteService(
            cache, mock(SubscriptionPoolManager.class), mock(DailyPriceMapper.class), calendar,
            new FixedSettings());

    @Test
    void premarketDoesNotExposePreviousIexQuoteAsRealtime() {
        when(calendar.currentSession()).thenReturn(MarketSession.PRE);
        when(cache.get("AAPL")).thenReturn(Optional.of(quote(true, false)));

        QuoteResponse result = service.quote("AAPL").orElseThrow();

        assertThat(result.live()).isFalse();
        assertThat(result.delayed()).isFalse();
    }

    @Test
    void premarketKeepsDelayedSipFlag() {
        when(calendar.currentSession()).thenReturn(MarketSession.PRE);
        when(cache.get("AAPL")).thenReturn(Optional.of(quote(true, true)));

        QuoteResponse result = service.quote("AAPL").orElseThrow();

        assertThat(result.live()).isTrue();
        assertThat(result.delayed()).isTrue();
    }

    private QuoteResponse quote(boolean live, boolean delayed) {
        return new QuoteResponse("AAPL", new BigDecimal("100.00"), Instant.parse("2026-08-24T08:00:00Z"),
                live, delayed);
    }
}
