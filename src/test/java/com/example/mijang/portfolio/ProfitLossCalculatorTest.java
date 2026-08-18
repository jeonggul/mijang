package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.portfolio.dto.ProfitLossResponse;
import com.example.mijang.portfolio.dto.SymbolPnl;
import com.example.mijang.portfolio.service.ProfitLossCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 손익 요인 분해 계산.
 *
 * <p><b>이 서비스의 간판 기능이다.</b> 번 돈을 주가 때문인 것과 환율 때문인 것으로 가른다.
 * 두 값이 화면에 나란히 놓이므로 <b>합이 총손익과 어긋나면 즉시 눈에 띈다</b>(2.2).
 *
 * <p>DB 도 스프링도 부르지 않는다. 계산기가 그렇게 설계돼 있어서다.
 */
class ProfitLossCalculatorTest {

    private static final LocalDate ASOF = LocalDate.of(2026, 8, 18);

    private static SymbolPnl of(String symbol, String qty, String avgPrice,
                                String avgFx, String currentPrice) {
        return new SymbolPnl(symbol, new BigDecimal(qty), new BigDecimal(avgPrice),
                new BigDecimal(avgFx), currentPrice == null ? null : new BigDecimal(currentPrice));
    }

    private static ProfitLossResponse calc(String fxRate, SymbolPnl... holdings) {
        return ProfitLossCalculator.calculate(List.of(holdings), new BigDecimal(fxRate), ASOF, false);
    }

    @Nested
    @DisplayName("분해 항등식 — 두 손익의 합이 총손익이다")
    class 항등식 {

        /* 10주 · 평단 $100 · 평균환율 1300 → 현재가 $150 · 현재환율 1400
           주가손익 = 10 × (150−100) × 1400 = 700,000
           환차손익 = 10 × 100 × (1400−1300) = 100,000
           검산     = 10 × (150×1400 − 100×1300) = 800,000 */
        @Test
        @DisplayName("주가손익과 환차손익을 갈라 구한다")
        void 분해() {
            ProfitLossResponse r = calc("1400", of("AAPL", "10", "100", "1300", "150"));

            assertThat(r.pricePnlKrw()).isEqualByComparingTo("700000.00");
            assertThat(r.fxPnlKrw()).isEqualByComparingTo("100000.00");
            assertThat(r.totalPnlKrw()).isEqualByComparingTo("800000.00");
        }

        @Test
        @DisplayName("합계는 수량 × (현재가×현재환율 − 평단가×평균환율) 와 같다")
        void 검산() {
            ProfitLossResponse r = calc("1400", of("AAPL", "10", "100", "1300", "150"));

            BigDecimal expected = new BigDecimal("10")
                    .multiply(new BigDecimal("150").multiply(new BigDecimal("1400"))
                            .subtract(new BigDecimal("100").multiply(new BigDecimal("1300"))));
            assertThat(r.totalPnlKrw()).isEqualByComparingTo(expected);
        }

        /* 자리수가 딱 떨어지지 않는 값으로도 합이 어긋나지 않아야 한다.
           합계를 따로 계산하면 여기서 1원씩 벌어진다(2.2). */
        @Test
        @DisplayName("소수점 수량에서도 두 값을 더하면 정확히 합계다")
        void 반올림해도합이맞는다() {
            ProfitLossResponse r = calc("1416.0701",
                    of("AAPL", "0.526316", "190.37", "1333.33", "204.11"),
                    of("MSFT", "1.234567", "398.12", "1287.45", "412.66"));

            assertThat(r.pricePnlKrw().add(r.fxPnlKrw())).isEqualByComparingTo(r.totalPnlKrw());
        }
    }

    @Nested
    @DisplayName("상쇄 판정 — 이 상태를 보여주려고 만든 서비스다")
    class 상쇄 {

        @Test
        @DisplayName("둘 다 오르면 BOTH_POSITIVE")
        void 둘다양수() {
            ProfitLossResponse r = calc("1400", of("AAPL", "10", "100", "1300", "150"));
            assertThat(r.state()).isEqualTo("BOTH_POSITIVE");
        }

        /* 주가는 올랐는데 환율이 떨어져 수익이 깎이는 상황 — OFFSET */
        @Test
        @DisplayName("주가는 올랐는데 환율이 떨어지면 OFFSET")
        void 상쇄() {
            ProfitLossResponse r = calc("1300", of("AAPL", "10", "100", "1400", "150"));

            assertThat(r.pricePnlKrw()).isEqualByComparingTo("650000.00");
            assertThat(r.fxPnlKrw()).isEqualByComparingTo("-100000.00");
            assertThat(r.state()).isEqualTo("OFFSET");
        }

        @Test
        @DisplayName("주가가 떨어지고 환율이 오르면 OFFSET")
        void 반대방향상쇄() {
            ProfitLossResponse r = calc("1400", of("AAPL", "10", "150", "1300", "100"));
            assertThat(r.state()).isEqualTo("OFFSET");
        }

        @Test
        @DisplayName("둘 다 내리면 BOTH_NEGATIVE")
        void 둘다음수() {
            ProfitLossResponse r = calc("1300", of("AAPL", "10", "150", "1400", "100"));

            assertThat(r.pricePnlKrw()).isEqualByComparingTo("-650000.00");
            assertThat(r.fxPnlKrw()).isEqualByComparingTo("-150000.00");
            assertThat(r.totalPnlKrw()).isEqualByComparingTo("-800000.00");
            assertThat(r.state()).isEqualTo("BOTH_NEGATIVE");
        }

        /* 명세의 조건이 "주가 ≥ 0, 환율 ≥ 0" 이므로 0 은 양수 쪽이다 */
        @Test
        @DisplayName("변동이 없으면 BOTH_POSITIVE 다")
        void 변동없음() {
            ProfitLossResponse r = calc("1300", of("AAPL", "10", "100", "1300", "100"));

            assertThat(r.pricePnlKrw()).isEqualByComparingTo("0.00");
            assertThat(r.fxPnlKrw()).isEqualByComparingTo("0.00");
            assertThat(r.state()).isEqualTo("BOTH_POSITIVE");
        }
    }

    @Nested
    @DisplayName("종목별로 계산해서 합친다")
    class 합산 {

        /* 평단가와 평균환율이 다른 두 종목. 전체를 한 번에 계산하면 틀린다(2.4).
           AAPL 700,000 + 100,000 / DIS 350,000 + 200,000 */
        @Test
        @DisplayName("평단가·평균환율이 다른 종목을 각각 계산해 더한다")
        void 두종목() {
            ProfitLossResponse r = calc("1400",
                    of("AAPL", "10", "100", "1300", "150"),
                    of("DIS", "5", "200", "1200", "250"));

            assertThat(r.pricePnlKrw()).isEqualByComparingTo("1050000.00");
            assertThat(r.fxPnlKrw()).isEqualByComparingTo("300000.00");
            assertThat(r.totalPnlKrw()).isEqualByComparingTo("1350000.00");
        }

        @Test
        @DisplayName("평가금액과 매입원가도 함께 쌓인다")
        void 평가금액과원가() {
            ProfitLossResponse r = calc("1400",
                    of("AAPL", "10", "100", "1300", "150"),
                    of("DIS", "5", "200", "1200", "250"));

            // 평가 USD = 10×150 + 5×250 = 2750, KRW = 2750 × 1400
            assertThat(r.totalValueUsd()).isEqualByComparingTo("2750");
            assertThat(r.totalValueKrw()).isEqualByComparingTo("3850000.00");
            // 원가 = 10×100×1300 + 5×200×1200 = 1,300,000 + 1,200,000
            assertThat(r.costBasisKrw()).isEqualByComparingTo("2500000.00");
        }
    }

    @Nested
    @DisplayName("현재가가 없는 종목은 뺀다")
    class 제외 {

        @Test
        @DisplayName("일봉이 없는 종목은 계산에서 빠지고 개수가 남는다")
        void 현재가없음() {
            ProfitLossResponse r = calc("1400",
                    of("AAPL", "10", "100", "1300", "150"),
                    of("NEW", "3", "50", "1300", null));

            assertThat(r.skippedSymbols()).isEqualTo(1);
            // 빠진 종목이 0 으로 잡히면 전량 손실이 되어 합계가 크게 틀어진다
            assertThat(r.totalPnlKrw()).isEqualByComparingTo("800000.00");
        }

        @Test
        @DisplayName("수량이 0 인 종목도 빠진다")
        void 수량0() {
            ProfitLossResponse r = calc("1400",
                    of("AAPL", "10", "100", "1300", "150"),
                    of("SOLD", "0", "100", "1300", "150"));

            assertThat(r.skippedSymbols()).isEqualTo(1);
            assertThat(r.totalPnlKrw()).isEqualByComparingTo("800000.00");
        }
    }

    @Nested
    @DisplayName("달러 기준에는 환차손익이 없다")
    class 달러기준 {

        @Test
        @DisplayName("달러 주가손익은 환율을 곱하지 않는다")
        void 달러주가손익() {
            ProfitLossResponse r = calc("1400", of("AAPL", "10", "100", "1300", "150"));

            assertThat(r.pricePnlUsd()).isEqualByComparingTo("500");
        }

        /* 달러로 보면 환율 변동은 손익이 아니다(2.7) */
        @Test
        @DisplayName("달러 총손익은 달러 주가손익과 같다")
        void 달러총손익() {
            ProfitLossResponse r = calc("1300", of("AAPL", "10", "100", "1400", "150"));

            assertThat(r.totalPnlUsd()).isEqualByComparingTo(r.pricePnlUsd());
            // 원화로는 환차손익이 −100,000 이지만 달러에는 그 항이 없다
            assertThat(r.fxPnlKrw()).isEqualByComparingTo("-100000.00");
        }
    }

    @Nested
    @DisplayName("수익률의 분모는 매입 원가다")
    class 수익률 {

        /* 800,000 / 1,300,000 = 0.615384… → 소수 4자리 0.6154 */
        @Test
        @DisplayName("총손익 ÷ 매입원가")
        void 원가기준() {
            ProfitLossResponse r = calc("1400", of("AAPL", "10", "100", "1300", "150"));

            assertThat(r.returnRate()).isEqualByComparingTo("0.6154");
        }

        /* 100원이 150원이 되면 50%(원가 기준)지 33%(평가액 기준)가 아니다 */
        @Test
        @DisplayName("평가금액이 아니라 원가로 나눈다")
        void 평가액이아니다() {
            ProfitLossResponse r = calc("1300", of("AAPL", "1", "100", "1300", "150"));

            assertThat(r.returnRate()).isEqualByComparingTo("0.5000");
        }
    }

    @Nested
    @DisplayName("경계")
    class 경계 {

        @Test
        @DisplayName("보유가 없으면 전부 0 이고 0 으로 나누지 않는다")
        void 보유없음() {
            ProfitLossResponse r = calc("1400");

            assertThat(r.pricePnlKrw()).isEqualByComparingTo("0.00");
            assertThat(r.fxPnlKrw()).isEqualByComparingTo("0.00");
            assertThat(r.totalPnlKrw()).isEqualByComparingTo("0.00");
            assertThat(r.costBasisKrw()).isEqualByComparingTo("0.00");
            assertThat(r.returnRate()).isEqualByComparingTo("0");
            assertThat(r.skippedSymbols()).isZero();
            assertThat(r.state()).isEqualTo("BOTH_POSITIVE");
        }

        @Test
        @DisplayName("전 종목이 계산 불가면 0 이고 전부 빠진 것으로 센다")
        void 전부제외() {
            ProfitLossResponse r = calc("1400",
                    of("A", "1", "100", "1300", null),
                    of("B", "2", "100", "1300", null));

            assertThat(r.skippedSymbols()).isEqualTo(2);
            assertThat(r.totalPnlKrw()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("적용 환율과 대체 여부를 그대로 담는다")
        void 응답메타() {
            ProfitLossResponse r = ProfitLossCalculator.calculate(
                    List.of(of("AAPL", "10", "100", "1300", "150")),
                    new BigDecimal("1400"), ASOF, true);

            assertThat(r.asOf()).isEqualTo(ASOF);
            assertThat(r.appliedFxRate()).isEqualByComparingTo("1400");
            assertThat(r.fxSubstituted()).isTrue();
        }
    }
}
