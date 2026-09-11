package com.example.mijang.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.mijang.dividend.domain.StockDividend;
import com.example.mijang.dividend.domain.StockEarnings;
import com.example.mijang.dividend.mapper.StockDividendMapper;
import com.example.mijang.dividend.mapper.StockEarningsMapper;
import com.example.mijang.news.dto.CalendarEventResponse;
import com.example.mijang.news.service.CalendarService;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.stock.mapper.WatchlistMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 캘린더 병합 로직. DB 없이 매퍼를 스텁·목으로 세워 병합·필터·이벤트 변환을 검증한다.
 */
class CalendarServiceTest {

    private final LocalDate from = LocalDate.of(2026, 9, 1);
    private final LocalDate to = LocalDate.of(2026, 9, 30);

    @Test
    void earningsMapsAndFiltersByMine() {
        var earnings = new StockEarnings("AAPL", LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 8, 31), new BigDecimal("2.35"), "post-market");
        var other = new StockEarnings("ZZZ", LocalDate.of(2026, 9, 12),
                null, null, null);
        StockEarningsMapper em = stubEarnings(List.of(earnings, other));
        CalendarService svc = new CalendarService(em, null, null, null);

        // 전체
        List<CalendarEventResponse> all = svc.earnings(from, to, Set.of());
        assertThat(all).extracting(CalendarEventResponse::symbol)
                .containsExactlyInAnyOrder("AAPL", "ZZZ");
        assertThat(all).allMatch(e -> e.type().equals("EARNINGS"));

        // 내 종목만
        List<CalendarEventResponse> mine = svc.earnings(from, to, Set.of("AAPL"));
        assertThat(mine).extracting(CalendarEventResponse::symbol).containsExactly("AAPL");
    }

    @Test
    void earningsNoteCombinesTimeOfDayAndEstimate() {
        var earnings = new StockEarnings("AAPL", LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 8, 31), new BigDecimal("2.35"), "post-market");
        StockEarningsMapper em = stubEarnings(List.of(earnings));
        CalendarService svc = new CalendarService(em, null, null, null);

        List<CalendarEventResponse> all = svc.earnings(from, to, Set.of());
        assertThat(all).hasSize(1);
        assertThat(all.get(0).note()).isEqualTo("장후 · EPS 2.35");
        assertThat(all.get(0).title()).isEqualTo("실적 발표");
    }

    @Test
    void dividendRowBecomesTwoEventsWithinRange() {
        // exDate 는 구간 안, payableDate 는 구간 밖 — 지급 이벤트는 빠져야 한다
        var d1 = dividend("AAPL", LocalDate.of(2026, 9, 5), new BigDecimal("0.25"),
                LocalDate.of(2026, 10, 15));
        // 둘 다 구간 안
        var d2 = dividend("MSFT", LocalDate.of(2026, 9, 8), new BigDecimal("0.75"),
                LocalDate.of(2026, 9, 20));
        // payableDate 가 null 이면 지급 이벤트를 건너뛴다
        var d3 = dividend("KO", LocalDate.of(2026, 9, 9), new BigDecimal("0.48"), null);

        StockDividendMapper dm = mock(StockDividendMapper.class);
        when(dm.findByExDateBetween(from, to)).thenReturn(List.of(d1, d2, d3));
        CalendarService svc = new CalendarService(null, dm, null, null);

        List<CalendarEventResponse> events = svc.dividends(from, to, Set.of());

        assertThat(events).hasSize(4); // AAPL 락일, MSFT 락일+지급, KO 락일
        assertThat(events).allMatch(e -> e.type().equals("DIVIDEND"));
        assertThat(events).filteredOn(e -> e.symbol().equals("MSFT"))
                .extracting(CalendarEventResponse::title)
                .containsExactlyInAnyOrder("배당락", "배당 지급");
        assertThat(events).filteredOn(e -> e.symbol().equals("AAPL"))
                .extracting(CalendarEventResponse::title)
                .containsExactly("배당락");
        var msftExDate = events.stream()
                .filter(e -> e.symbol().equals("MSFT") && e.title().equals("배당락"))
                .findFirst().orElseThrow();
        assertThat(msftExDate.note()).isEqualTo("$0.75");
    }

    @Test
    void dividendsFilterByMine() {
        var d1 = dividend("AAPL", LocalDate.of(2026, 9, 5), new BigDecimal("0.25"), null);
        var d2 = dividend("ZZZ", LocalDate.of(2026, 9, 6), new BigDecimal("0.10"), null);
        StockDividendMapper dm = mock(StockDividendMapper.class);
        when(dm.findByExDateBetween(from, to)).thenReturn(List.of(d1, d2));
        CalendarService svc = new CalendarService(null, dm, null, null);

        List<CalendarEventResponse> mine = svc.dividends(from, to, Set.of("AAPL"));
        assertThat(mine).extracting(CalendarEventResponse::symbol).containsExactly("AAPL");
    }

    @Test
    void mySymbolsIsUnionOfHoldingsAndWatchlistUppercased() {
        TransactionMapper tm = mock(TransactionMapper.class);
        WatchlistMapper wm = mock(WatchlistMapper.class);
        when(tm.findSymbolsByUser(7L)).thenReturn(List.of("aapl", "MSFT"));
        when(wm.findSymbolsByUser(7L)).thenReturn(List.of("msft", "goog"));
        CalendarService svc = new CalendarService(null, null, tm, wm);

        Set<String> mine = svc.mySymbols(7L);

        assertThat(mine).containsExactlyInAnyOrder("AAPL", "MSFT", "GOOG");
    }

    @Test
    void mySymbolsIsEmptyWhenUserIdNull() {
        CalendarService svc = new CalendarService(null, null, null, null);
        assertThat(svc.mySymbols(null)).isEmpty();
    }

    @Test
    void nextEventsReturnsNullFieldsWhenNothingScheduled() {
        StockEarningsMapper em = mock(StockEarningsMapper.class);
        StockDividendMapper dm = mock(StockDividendMapper.class);
        CalendarService svc = new CalendarService(em, dm, null, null);

        CalendarService.NextEvents next = svc.nextEvents("aapl");

        assertThat(next.earnings()).isNull();
        assertThat(next.dividend()).isNull();
    }

    @Test
    void nextEventsMapsEarningsAndDividend() {
        var earnings = new StockEarnings("AAPL", LocalDate.of(2026, 10, 1), null,
                new BigDecimal("2.5"), "pre-market");
        var div = dividend("AAPL", LocalDate.of(2026, 10, 5), new BigDecimal("0.25"), null);

        StockEarningsMapper em = mock(StockEarningsMapper.class);
        StockDividendMapper dm = mock(StockDividendMapper.class);
        when(em.findNextBySymbol(org.mockito.ArgumentMatchers.eq("AAPL"),
                org.mockito.ArgumentMatchers.any())).thenReturn(earnings);
        when(dm.findNextExDateBySymbol(org.mockito.ArgumentMatchers.eq("AAPL"),
                org.mockito.ArgumentMatchers.any())).thenReturn(div);
        CalendarService svc = new CalendarService(em, dm, null, null);

        CalendarService.NextEvents next = svc.nextEvents("aapl");

        assertThat(next.earnings().symbol()).isEqualTo("AAPL");
        assertThat(next.earnings().date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(next.dividend().symbol()).isEqualTo("AAPL");
        assertThat(next.dividend().date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(next.dividend().note()).isEqualTo("$0.25");
    }

    private StockDividend dividend(String symbol, LocalDate exDate, BigDecimal amount, LocalDate payableDate) {
        return new StockDividend(symbol, exDate, "CASH", amount, exDate.minusDays(2), payableDate,
                null, false, false, null, null);
    }

    private StockEarningsMapper stubEarnings(List<StockEarnings> rows) {
        return new StockEarningsMapper() {
            public int upsert(StockEarnings e) { return 1; }
            public List<StockEarnings> findByReportDateBetween(LocalDate f, LocalDate t) { return rows; }
            public StockEarnings findNextBySymbol(String s, LocalDate d) { return null; }
        };
    }
}
