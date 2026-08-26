package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.common.time.MarketCalendar;
import com.example.mijang.user.dto.DividendExDateHit;
import com.example.mijang.user.dto.DividendPayHit;
import com.example.mijang.user.dto.NotificationResponse;
import com.example.mijang.user.dto.NotificationSettingsForm;
import com.example.mijang.user.dto.NotificationSettingsResponse;
import com.example.mijang.user.dto.TargetPriceHit;
import com.example.mijang.user.dto.VolatilityHit;
import com.example.mijang.user.mapper.NotificationMapper;
import com.example.mijang.user.service.NotificationProducerService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 알림 생성 문구와 연결.
 *
 * <p>후보 판정은 SQL 이 하므로 실서버(백필)로 확인했다. 여기서 고정하는 것은
 * <b>문구와 링크</b>다 — 목표가는 회고로, 급등락은 종목으로 가야 하고,
 * 부호와 방향 표현이 헷갈리면 알림이 오보가 된다.
 */
class NotificationProducerTest {

    private static class Notifications implements NotificationMapper {
        List<TargetPriceHit> targetHits = List.of();
        List<VolatilityHit> volatilityHits = List.of();
        List<DividendExDateHit> dividendExDateHits = List.of();
        List<DividendPayHit> dividendPayHits = List.of();
        final List<String> inserted = new ArrayList<>();

        @Override public int insert(Long userId, String type, String symbol,
                                    String title, String body, String linkUrl) {
            inserted.add(type + "|" + title + "|" + body + "|" + linkUrl);
            return 1;
        }
        @Override public List<TargetPriceHit> findTargetPriceHits(LocalDate d) { return targetHits; }
        @Override public List<VolatilityHit> findVolatilityHits(LocalDate d) { return volatilityHits; }
        @Override public List<DividendExDateHit> findDividendExDateHits(LocalDate d) {
            return dividendExDateHits;
        }
        @Override public List<DividendPayHit> findDividendPayHits() { return dividendPayHits; }
        @Override public List<NotificationResponse> findRecent(Long u, int l) { return List.of(); }
        @Override public int markAllRead(Long userId) { return 0; }
        @Override public NotificationSettingsResponse findSettings(Long userId) { return null; }
        @Override public int upsertSettings(Long u, NotificationSettingsForm f) { return 1; }
    }

    private final LocalDate date = LocalDate.of(2026, 8, 21);

    @Test
    @DisplayName("목표가 알림은 회고 화면으로 보낸다")
    void 목표가() {
        Notifications mapper = new Notifications();
        mapper.targetHits = List.of(new TargetPriceHit(1L, "AAPL",
                new BigDecimal("313.50"), new BigDecimal("313.55")));

        int count = new NotificationProducerService(mapper, new MarketCalendar()).produce(date);

        assertThat(count).isEqualTo(1);
        assertThat(mapper.inserted.get(0))
                .startsWith("TARGET_PRICE|AAPL 목표가 도달|")
                .contains("$313.50")
                .endsWith("|/retrospect");     // 스키마 주석 — 목표가 알림은 회고로
    }

    @Test
    @DisplayName("급등은 +, 급락은 − 로 방향이 붙는다")
    void 급등락방향() {
        Notifications mapper = new Notifications();
        mapper.volatilityHits = List.of(
                new VolatilityHit(1L, "TSLA", new BigDecimal("339.31"),
                        new BigDecimal("362.78"), new BigDecimal("0.0692")),
                new VolatilityHit(1L, "PLTR", new BigDecimal("100.00"),
                        new BigDecimal("94.00"), new BigDecimal("-0.0600")));

        new NotificationProducerService(mapper, new MarketCalendar()).produce(date);

        assertThat(mapper.inserted.get(0))
                .contains("TSLA 급등").contains("+6.9%").contains("올랐습니다")
                .endsWith("|/stock?symbol=TSLA");
        assertThat(mapper.inserted.get(1))
                .contains("PLTR 급락").contains("−6.0%").contains("내렸습니다");
    }

    @Test
    @DisplayName("후보가 없으면 아무것도 만들지 않는다")
    void 없음() {
        Notifications mapper = new Notifications();

        int count = new NotificationProducerService(mapper, new MarketCalendar()).produce(date);

        assertThat(count).isZero();
        assertThat(mapper.inserted).isEmpty();
    }

    @Test
    @DisplayName("배당락일 알림은 날짜·주당 배당·지급일을 보여 주고 종목으로 보낸다")
    void 배당락일() {
        Notifications mapper = new Notifications();
        mapper.dividendExDateHits = List.of(new DividendExDateHit(
                1L, "AAPL", LocalDate.of(2026, 8, 23), LocalDate.of(2026, 8, 28),
                new BigDecimal("0.27")));

        int count = new NotificationProducerService(mapper, new MarketCalendar())
                .produceDividend(date);

        assertThat(count).isEqualTo(1);
        assertThat(mapper.inserted.get(0))
                .startsWith("DIVIDEND|AAPL 배당락일 안내|")
                .contains("8월 23일").contains("주당 $0.27").contains("8월 28일")
                .endsWith("|/stock?symbol=AAPL");
    }

    @Test
    @DisplayName("예상 배당 알림은 세후 금액을 보여 주고 배당 관리로 보낸다")
    void 배당지급() {
        Notifications mapper = new Notifications();
        mapper.dividendPayHits = List.of(new DividendPayHit(
                1L, "AAPL", LocalDate.of(2026, 8, 28), new BigDecimal("2.2950")));

        int count = new NotificationProducerService(mapper, new MarketCalendar())
                .produceDividend(date);

        assertThat(count).isEqualTo(1);
        assertThat(mapper.inserted.get(0))
                .startsWith("DIVIDEND|AAPL 배당 지급 예정|")
                .contains("8월 28일").contains("예상 세후 $2.30")
                .endsWith("|/dividend");
    }
}
