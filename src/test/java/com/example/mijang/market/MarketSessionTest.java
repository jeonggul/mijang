package com.example.mijang.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.market.domain.MarketSession;
import com.example.mijang.market.service.MarketCalendarService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 세션 판정. 등락률의 기준가와 화면의 멈춤 여부가 전부 이 판단 위에 있다.
 *
 * <p>장이 열리고 닫힐 때까지 기다릴 수 없으므로 시각을 넣어 확인한다.
 * 달력은 실제 DB 의 값을 쓴다 — 조기폐장·휴장이 진짜로 들어 있는지까지 함께 본다.
 */
@SpringBootTest
class MarketSessionTest {

    private static final ZoneId ET = ZoneId.of("America/New_York");

    @Autowired
    private MarketCalendarService calendarService;

    private MarketSession at(String date, int hour, int minute) {
        return calendarService.sessionAt(
                ZonedDateTime.of(LocalDate.parse(date), LocalTime.of(hour, minute), ET));
    }

    @Test
    @DisplayName("평상 거래일 — 시각에 따라 네 구간으로 갈린다")
    void 평상거래일() {
        String day = "2026-11-25";                 // 추수감사절 전날, 평상 마감
        assertThat(at(day, 3, 59)).isEqualTo(MarketSession.CLOSED);
        assertThat(at(day, 4, 0)).isEqualTo(MarketSession.PRE);
        assertThat(at(day, 9, 29)).isEqualTo(MarketSession.PRE);
        assertThat(at(day, 9, 30)).isEqualTo(MarketSession.REGULAR);
        assertThat(at(day, 15, 59)).isEqualTo(MarketSession.REGULAR);
        assertThat(at(day, 16, 0)).isEqualTo(MarketSession.AFTER);
        assertThat(at(day, 19, 59)).isEqualTo(MarketSession.AFTER);
        assertThat(at(day, 20, 0)).isEqualTo(MarketSession.CLOSED);
    }

    @Test
    @DisplayName("추수감사절은 휴장 — 정규장 시간에도 CLOSED")
    void 휴장일() {
        assertThat(at("2026-11-26", 11, 0)).isEqualTo(MarketSession.CLOSED);
    }

    @Test
    @DisplayName("조기폐장일은 13:00 에 정규장이 끝나고 17:00 에 시간외도 끝난다")
    void 조기폐장일() {
        String day = "2026-11-27";
        assertThat(at(day, 12, 59)).isEqualTo(MarketSession.REGULAR);
        assertThat(at(day, 13, 0)).isEqualTo(MarketSession.AFTER);
        assertThat(at(day, 16, 59)).isEqualTo(MarketSession.AFTER);
        assertThat(at(day, 17, 0)).isEqualTo(MarketSession.CLOSED);
    }

    @Test
    @DisplayName("주말은 달력에 없어 CLOSED")
    void 주말() {
        assertThat(at("2026-11-28", 11, 0)).isEqualTo(MarketSession.CLOSED);  // 토
        assertThat(at("2026-11-29", 11, 0)).isEqualTo(MarketSession.CLOSED);  // 일
    }

    @Test
    @DisplayName("직전 거래일은 주말·연휴를 건너뛴다")
    void 직전거래일() {
        assertThat(calendarService.previousTradingDay(LocalDate.parse("2026-11-30")))
                .contains(LocalDate.parse("2026-11-27"));   // 월요일의 직전은 금요일
        assertThat(calendarService.previousTradingDay(LocalDate.parse("2026-11-27")))
                .contains(LocalDate.parse("2026-11-25"));   // 추수감사절을 건너뛴다
    }

    @Test
    @DisplayName("CLOSED 가 아닌 구간만 값이 움직인다")
    void 값이움직이는구간() {
        assertThat(MarketSession.REGULAR.live()).isTrue();
        assertThat(MarketSession.AFTER.live()).isTrue();
        assertThat(MarketSession.PRE.live()).isTrue();
        assertThat(MarketSession.CLOSED.live()).isFalse();
    }
}
