package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.common.time.MarketCalendar;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.fx.dto.FxRateResponse;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.portfolio.dto.PeriodReturnResponse;
import com.example.mijang.portfolio.dto.SnapshotResponse;
import com.example.mijang.portfolio.dto.SymbolPnl;
import com.example.mijang.portfolio.mapper.DailySnapshotMapper;
import com.example.mijang.portfolio.mapper.HoldingMapper;
import com.example.mijang.portfolio.mapper.PortfolioMapper;
import com.example.mijang.portfolio.service.SnapshotService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 스냅샷과 기간 리포트.
 *
 * <p>DB 도 스프링도 부르지 않는다. 매퍼는 손으로 만든 가짜를 끼운다 —
 * 여기서 보려는 것은 <b>어떤 값을 고르고 어떻게 나누는가</b> 뿐이다.
 */
class SnapshotServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 8, 17);   // 월요일
    private static final LocalDate SAT = LocalDate.of(2026, 8, 15);   // 토요일

    private static SnapshotResponse snap(LocalDate date, String valueKrw) {
        return new SnapshotResponse(date, new BigDecimal(valueKrw), new BigDecimal("500000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1400"), false);
    }

    /** 넣어 둔 것을 그대로 돌려주고, 저장된 것을 모아 두는 가짜 매퍼. */
    private static class Snapshots implements DailySnapshotMapper {
        SnapshotResponse first;
        SnapshotResponse last;
        List<Long> userIds = new ArrayList<>();
        final List<LocalDate> savedDates = new ArrayList<>();
        final List<BigDecimal> savedValues = new ArrayList<>();

        @Override public int upsert(Long userId, Long portfolioId, LocalDate date,
                                    BigDecimal marketValueUsd, BigDecimal marketValueKrw,
                                    BigDecimal costBasisKrw, BigDecimal pricePnlKrw,
                                    BigDecimal fxPnlKrw, BigDecimal totalPnlKrw,
                                    BigDecimal returnRate, BigDecimal fxRate, boolean fxSubstituted) {
            savedDates.add(date);
            savedValues.add(marketValueKrw);
            return 1;
        }

        @Override public List<SnapshotResponse> findByRange(Long userId, LocalDate from, LocalDate to) {
            return List.of();
        }

        @Override public SnapshotResponse findFirstOnOrAfter(Long userId, LocalDate from) { return first; }
        @Override public SnapshotResponse findLastOnOrBefore(Long userId, LocalDate to) { return last; }
        @Override public List<Long> findUserIdsWithHoldings() { return userIds; }
    }

    /** 어느 경로로 불렸는지 기록하는 가짜 보유 매퍼. */
    private static class Holdings implements HoldingMapper {
        List<SymbolPnl> rows = new ArrayList<>();
        LocalDate askedAsOf;
        boolean askedLatest;

        @Override public List<com.example.mijang.portfolio.dto.HoldingResponse>
                findByUser(Long userId, BigDecimal fxRate) { return List.of(); }

        @Override public int upsert(Long userId, Long portfolioId, String symbol,
                                    BigDecimal quantity, BigDecimal avgPrice, BigDecimal avgFxRate,
                                    BigDecimal totalFee, BigDecimal realizedPnlKrw) { return 1; }

        @Override public BigDecimal sumMarketValueKrw(Long userId, BigDecimal fxRate) { return null; }

        @Override public BigDecimal findQuantity(Long userId, String symbol) { return null; }

        @Override public List<SymbolPnl> findForPnl(Long userId, String symbol) {
            askedLatest = true;      // 이 경로로 오면 과거 스냅샷이 오늘 종가로 찍힌다
            return rows;
        }

        @Override public List<SymbolPnl> findForPnlAsOf(Long userId, String symbol, LocalDate asOf) {
            askedAsOf = asOf;
            return rows;
        }
    }

    private static class Portfolios implements PortfolioMapper {
        @Override public Long findDefaultId(Long userId) { return 1L; }
        @Override public int insertDefault(Long userId) { return 1; }
        @Override public Long findLastInsertedId() { return 1L; }
    }

    /** 그날 확정 환율만 흉내 낸다. */
    private static class Fx extends FxRateService {
        FxRateResponse value;
        Fx() { super(null, null, null); }
        @Override public Optional<FxRateResponse> findByDate(LocalDate date) {
            return Optional.ofNullable(value);
        }
    }

    private static Fx fxOf(String rate) {
        Fx fx = new Fx();
        fx.value = new FxRateResponse(new BigDecimal(rate), MON, false, null, null);
        return fx;
    }

    private static SnapshotService service(Snapshots s, Holdings h, Fx fx) {
        return new SnapshotService(s, h, new Portfolios(), fx,
                new MarketCalendar(), new TradingClock());
    }

    @Nested
    @DisplayName("기간 수익률 — 스냅샷 두 건만 읽는다")
    class 기간수익률 {

        @Test
        @DisplayName("(끝 − 시작) ÷ 시작")
        void 계산() {
            Snapshots s = new Snapshots();
            s.first = snap(MON, "1000000");
            s.last = snap(MON.plusDays(10), "1250000");

            PeriodReturnResponse r = service(s, new Holdings(), fxOf("1400"))
                    .periodReturn(1L, MON, MON.plusDays(10));

            assertThat(r.startValueKrw()).isEqualByComparingTo("1000000");
            assertThat(r.endValueKrw()).isEqualByComparingTo("1250000");
            assertThat(r.changeKrw()).isEqualByComparingTo("250000");
            assertThat(r.returnRate()).isEqualByComparingTo("0.2500");
        }

        @Test
        @DisplayName("손실이면 음수로 나온다")
        void 손실() {
            Snapshots s = new Snapshots();
            s.first = snap(MON, "1000000");
            s.last = snap(MON.plusDays(5), "800000");

            PeriodReturnResponse r = service(s, new Holdings(), fxOf("1400"))
                    .periodReturn(1L, MON, MON.plusDays(5));

            assertThat(r.changeKrw()).isEqualByComparingTo("-200000");
            assertThat(r.returnRate()).isEqualByComparingTo("-0.2000");
        }

        /* 시작일이 주말이면 그날 스냅샷이 없다. 이후 첫 행을 시작점으로 삼는다(2.1) */
        @Test
        @DisplayName("시작일에 스냅샷이 없으면 이후 첫 행이 기준일이 된다")
        void 시작일보정() {
            Snapshots s = new Snapshots();
            s.first = snap(MON, "1000000");            // 토요일로 물었지만 월요일 행이 온다
            s.last = snap(MON.plusDays(5), "1100000");

            PeriodReturnResponse r = service(s, new Holdings(), fxOf("1400"))
                    .periodReturn(1L, SAT, MON.plusDays(5));

            assertThat(r.from()).isEqualTo(MON);
        }

        @Test
        @DisplayName("스냅샷이 없으면 null 이다")
        void 스냅샷없음() {
            assertThat(service(new Snapshots(), new Holdings(), fxOf("1400"))
                    .periodReturn(1L, MON, MON.plusDays(5))).isNull();
        }

        /* 시작이 끝보다 뒤면 구간이 성립하지 않는다 */
        @Test
        @DisplayName("시작이 끝보다 뒤면 null 이다")
        void 뒤집힌구간() {
            Snapshots s = new Snapshots();
            s.first = snap(MON.plusDays(10), "1000000");
            s.last = snap(MON, "900000");

            assertThat(service(s, new Holdings(), fxOf("1400"))
                    .periodReturn(1L, MON, MON.plusDays(10))).isNull();
        }

        @Test
        @DisplayName("시작 평가액이 0 이면 0 으로 나누지 않는다")
        void 시작0() {
            Snapshots s = new Snapshots();
            s.first = snap(MON, "0");
            s.last = snap(MON.plusDays(5), "500000");

            PeriodReturnResponse r = service(s, new Holdings(), fxOf("1400"))
                    .periodReturn(1L, MON, MON.plusDays(5));

            assertThat(r.returnRate()).isEqualByComparingTo("0");
            assertThat(r.changeKrw()).isEqualByComparingTo("500000");
        }
    }

    @Nested
    @DisplayName("스냅샷 찍기")
    class 스냅샷 {

        private Holdings held() {
            Holdings h = new Holdings();
            h.rows = List.of(new SymbolPnl("AAPL", new BigDecimal("10"), new BigDecimal("100"),
                    new BigDecimal("1300"), new BigDecimal("150")));
            return h;
        }

        /* 주말에 찍으면 같은 값이 반복되어 차트에 평평한 구간이 생긴다(2.3) */
        @Test
        @DisplayName("거래일이 아니면 아무 것도 찍지 않는다")
        void 휴장일() {
            Snapshots s = new Snapshots();
            s.userIds = List.of(1L);

            assertThat(service(s, held(), fxOf("1400")).createDailySnapshot(SAT)).isZero();
            assertThat(s.savedDates).isEmpty();
        }

        /* 환율은 두 항 모두에 곱해진다. 없으면 손익이 성립하지 않는다(dashboard 2.6) */
        @Test
        @DisplayName("그날 환율이 없으면 찍지 않는다")
        void 환율없음() {
            Snapshots s = new Snapshots();
            s.userIds = List.of(1L);

            assertThat(service(s, held(), new Fx()).createDailySnapshot(MON)).isZero();
            assertThat(s.savedDates).isEmpty();
        }

        /* 행을 만들면 평가액 0 이 이어지고, 나중에 매수했을 때 수익률이 무한대가 된다(2.6) */
        @Test
        @DisplayName("보유가 없는 사용자는 건너뛴다")
        void 보유없음() {
            Snapshots s = new Snapshots();
            s.userIds = List.of(1L);

            assertThat(service(s, new Holdings(), fxOf("1400")).createDailySnapshot(MON)).isZero();
            assertThat(s.savedDates).isEmpty();
        }

        @Test
        @DisplayName("보유가 있으면 그날로 한 줄 찍는다")
        void 저장() {
            Snapshots s = new Snapshots();
            s.userIds = List.of(1L);

            assertThat(service(s, held(), fxOf("1400")).createDailySnapshot(MON)).isEqualTo(1);
            assertThat(s.savedDates).containsExactly(MON);
            // 10주 × $150 × 1400
            assertThat(s.savedValues.get(0)).isEqualByComparingTo("2100000.00");
        }

        /* 놓친 날을 메울 때 오늘 종가를 쓰면 과거 추이가 통째로 거짓이 된다(2.4) */
        @Test
        @DisplayName("그날 이하의 종가를 쓴다 — 최신 종가 경로로 가지 않는다")
        void 과거종가() {
            Snapshots s = new Snapshots();
            s.userIds = List.of(1L);
            Holdings h = held();

            service(s, h, fxOf("1400")).createDailySnapshot(MON);

            assertThat(h.askedAsOf).isEqualTo(MON);
            assertThat(h.askedLatest).isFalse();
        }

        /* 아직 오지 않은 날을 메우면 대체 환율로 미래 스냅샷이 생겨 차트가 앞으로 뻗는다 */
        @Test
        @DisplayName("메우기 — 미래 날짜는 거절한다")
        void 미래는못메운다() {
            Snapshots s = new Snapshots();
            LocalDate future = LocalDate.now().plusYears(1).with(java.time.DayOfWeek.MONDAY);

            assertThat(service(s, held(), fxOf("1400")).backfill(1L, future)).isFalse();
            assertThat(s.savedDates).isEmpty();
        }

        @Test
        @DisplayName("메우기 — 휴장일은 거절한다")
        void 메우기휴장일() {
            Snapshots s = new Snapshots();
            assertThat(service(s, held(), fxOf("1400")).backfill(1L, SAT)).isFalse();
            assertThat(s.savedDates).isEmpty();
        }

        @Test
        @DisplayName("메우기 — 부른 사람 것만 찍는다")
        void 메우기저장() {
            Snapshots s = new Snapshots();
            assertThat(service(s, held(), fxOf("1400")).backfill(1L, MON)).isTrue();
            assertThat(s.savedDates).containsExactly(MON);
        }

        @Test
        @DisplayName("여러 사용자를 각각 찍고 그 수를 돌려준다")
        void 여러사용자() {
            Snapshots s = new Snapshots();
            s.userIds = List.of(1L, 2L, 3L);

            assertThat(service(s, held(), fxOf("1400")).createDailySnapshot(MON)).isEqualTo(3);
            assertThat(s.savedDates).hasSize(3);
        }
    }
}
