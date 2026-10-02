package com.example.mijang.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.mijang.market.domain.MarketDay;
import com.example.mijang.market.service.MarketCalendarService;
import com.example.mijang.stock.domain.ChartRange;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChartServiceWindowTest {

    private static final ZoneId ET = ZoneId.of("America/New_York");

    @Mock
    private MarketCalendarService marketCalendarService;

    @InjectMocks
    private ChartService chartService;

    @Test
    void premarketBeforeOpenUsesPreviousTradingSession() {
        LocalDate monday = LocalDate.of(2026, 8, 24);
        MarketDay mondaySession = day(monday);
        MarketDay fridaySession = day(LocalDate.of(2026, 8, 21));
        when(marketCalendarService.tradingDay(monday)).thenReturn(Optional.of(mondaySession));
        when(marketCalendarService.previousTradingDayInfo(monday)).thenReturn(Optional.of(fridaySession));

        Instant now = at(monday, LocalTime.of(3, 30));
        ChartService.ChartWindow live = chartService.windowOf(ChartRange.LIVE, now);
        ChartService.ChartWindow oneDay = chartService.windowOf(ChartRange.ONE_DAY, now);

        assertThat(live.from()).isEqualTo(at(fridaySession.tradeDate(), LocalTime.of(15, 0)));
        assertThat(live.to()).isEqualTo(at(fridaySession.tradeDate(), LocalTime.of(20, 0)));
        assertThat(live.currentSession()).isFalse();
        assertThat(oneDay.from()).isEqualTo(at(fridaySession.tradeDate(), LocalTime.of(4, 0)));
        assertThat(oneDay.to()).isEqualTo(at(fridaySession.tradeDate(), LocalTime.of(20, 0)));
    }

    @Test
    void afterCloseUsesCompletedCurrentSession() {
        LocalDate monday = LocalDate.of(2026, 8, 24);
        when(marketCalendarService.tradingDay(monday)).thenReturn(Optional.of(day(monday)));

        ChartService.ChartWindow window = chartService.windowOf(
                ChartRange.LIVE, at(monday, LocalTime.of(21, 0)));

        assertThat(window.from()).isEqualTo(at(monday, LocalTime.of(15, 0)));
        assertThat(window.to()).isEqualTo(at(monday, LocalTime.of(20, 0)));
    }

    @Test
    void liveDuringSessionKeepsRollingFiveHoursWithinSessionOpen() {
        LocalDate monday = LocalDate.of(2026, 8, 24);
        when(marketCalendarService.tradingDay(monday)).thenReturn(Optional.of(day(monday)));

        ChartService.ChartWindow window = chartService.windowOf(
                ChartRange.LIVE, at(monday, LocalTime.of(6, 0)));

        assertThat(window.from()).isEqualTo(at(monday, LocalTime.of(4, 0)));
        assertThat(window.to()).isEqualTo(at(monday, LocalTime.of(6, 0)));
        assertThat(window.currentSession()).isTrue();
    }

    private MarketDay day(LocalDate date) {
        return new MarketDay(date, LocalTime.of(9, 30), LocalTime.of(16, 0),
                LocalTime.of(4, 0), LocalTime.of(20, 0));
    }

    private Instant at(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, ET).toInstant();
    }
}
