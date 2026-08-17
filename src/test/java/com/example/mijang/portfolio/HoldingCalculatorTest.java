package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.portfolio.domain.Holding;
import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.service.HoldingCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 보유 현황 계산.
 *
 * <p>이 서비스에서 <b>가장 틀리면 안 되는 코드</b>다. 평단가 하나가 어긋나면 손익·수익률·
 * 요인 분해가 전부 따라 틀어지는데, 화면에는 그럴듯한 숫자로 나와 눈에 띄지 않는다.
 *
 * <p>DB 도 스프링도 부르지 않는다. 계산기가 그렇게 설계돼 있어서다(2.10).
 */
class HoldingCalculatorTest {

    private static Transaction buy(String qty, String price, String fx, String fee, int day) {
        return tx("BUY", qty, price, fx, fee, day);
    }

    private static Transaction sell(String qty, String price, String fx, String fee, int day) {
        return tx("SELL", qty, price, fx, fee, day);
    }

    private static Transaction tx(String side, String qty, String price,
                                  String fx, String fee, int day) {
        LocalDate d = LocalDate.of(2026, 8, day);
        return new Transaction(null, 1L, 1L, "AAPL", side,
                new BigDecimal(qty), new BigDecimal(price), new BigDecimal(fx), new BigDecimal(fee),
                d.atStartOfDay(), d, null, null, null);
    }

    @Nested
    @DisplayName("평단가 — 이동평균")
    class 평단가 {

        @Test
        @DisplayName("첫 매수는 체결가가 그대로 평단가다")
        void 첫매수() {
            Holding h = HoldingCalculator.calculate("AAPL",
                    List.of(buy("10", "200", "1300", "0", 1)));

            assertThat(h.quantity()).isEqualByComparingTo("10");
            assertThat(h.avgPrice()).isEqualByComparingTo("200");
            assertThat(h.avgFxRate()).isEqualByComparingTo("1300");
        }

        @Test
        @DisplayName("두 번 사면 수량으로 가중평균한다")
        void 추가매수() {
            // (10×200 + 10×300) / 20 = 250
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("10", "200", "1300", "0", 1),
                    buy("10", "300", "1400", "0", 2)));

            assertThat(h.quantity()).isEqualByComparingTo("20");
            assertThat(h.avgPrice()).isEqualByComparingTo("250");
        }

        @Test
        @DisplayName("매도는 평단가를 바꾸지 않는다")
        void 매도는평단유지() {
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("10", "200", "1300", "0", 1),
                    sell("4", "500", "1400", "0", 2)));

            assertThat(h.quantity()).isEqualByComparingTo("6");
            // 판 것은 남은 것의 매입 원가와 무관하다(2.3)
            assertThat(h.avgPrice()).isEqualByComparingTo("200");
            assertThat(h.avgFxRate()).isEqualByComparingTo("1300");
        }
    }

    @Nested
    @DisplayName("평균매수환율 — 금액가중")
    class 평균환율 {

        @Test
        @DisplayName("수량이 아니라 금액으로 가중한다")
        void 금액가중() {
            /* 1주 $500(환율 1300) + 100주 $5(환율 1400)
               금액가중: (500×1300 + 500×1400) / 1000 = 1350
               수량가중이라면 (1×1300 + 100×1400)/101 ≈ 1399 로 크게 달라진다 */
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("1", "500", "1300", "0", 1),
                    buy("100", "5", "1400", "0", 2)));

            assertThat(h.avgFxRate()).isEqualByComparingTo("1350.0000");
        }

        @Test
        @DisplayName("싼 종목을 많이 사도 환율이 과대평가되지 않는다")
        void 수량가중과다르다() {
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("1", "500", "1300", "0", 1),
                    buy("100", "5", "1400", "0", 2)));

            // 수량으로 가중했다면 1399 근처가 된다
            assertThat(h.avgFxRate()).isLessThan(new BigDecimal("1360"));
        }
    }

    @Nested
    @DisplayName("실현손익")
    class 실현손익 {

        @Test
        @DisplayName("매도가 확정한 원화 손익을 쌓는다")
        void 매도손익() {
            /* 10주를 $200·1300원에 사서 4주를 $500·1400원에 팔았다
               4 × (500×1400 − 200×1300) = 4 × (700,000 − 260,000) = 1,760,000 */
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("10", "200", "1300", "0", 1),
                    sell("4", "500", "1400", "0", 2)));

            assertThat(h.realizedPnlKrw()).isEqualByComparingTo("1760000.00");
        }

        @Test
        @DisplayName("수수료를 빼고 계산한다")
        void 수수료차감() {
            /* 위와 같되 매도 수수료 $10 — 10 × 1400 = 14,000 원을 뺀다 */
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("10", "200", "1300", "0", 1),
                    sell("4", "500", "1400", "10", 2)));

            assertThat(h.realizedPnlKrw()).isEqualByComparingTo("1746000.00");
        }

        @Test
        @DisplayName("손실도 그대로 남긴다")
        void 손실() {
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("10", "300", "1400", "0", 1),
                    sell("10", "200", "1300", "0", 2)));

            assertThat(h.realizedPnlKrw()).isNegative();
        }

        @Test
        @DisplayName("여러 번 팔면 쌓인다")
        void 누적() {
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("10", "200", "1300", "0", 1),
                    sell("2", "500", "1400", "0", 2),
                    sell("2", "500", "1400", "0", 3)));

            assertThat(h.realizedPnlKrw()).isEqualByComparingTo("1760000.00");
        }
    }

    @Nested
    @DisplayName("경계")
    class 경계 {

        @Test
        @DisplayName("전량 매도하면 수량 0 이지만 실현손익은 남는다")
        void 전량매도() {
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("10", "200", "1300", "0", 1),
                    sell("10", "500", "1400", "0", 2)));

            assertThat(h.quantity()).isEqualByComparingTo("0");
            assertThat(h.held()).isFalse();
            // 행을 지우면 이 값이 사라진다(2.4)
            assertThat(h.realizedPnlKrw()).isEqualByComparingTo("4400000.00");
        }

        @Test
        @DisplayName("전량 매도 후 재매수는 새 값이 평균이 된다")
        void 재매수() {
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("10", "200", "1300", "0", 1),
                    sell("10", "500", "1400", "0", 2),
                    buy("5", "100", "1200", "0", 3)));

            assertThat(h.quantity()).isEqualByComparingTo("5");
            assertThat(h.avgPrice()).isEqualByComparingTo("100");
            assertThat(h.avgFxRate()).isEqualByComparingTo("1200");
        }

        @Test
        @DisplayName("보유량을 넘겨 팔면 음수를 그대로 돌려준다")
        void 초과매도() {
            /* 계산기는 규칙이 아니라 산수만 책임진다.
               거절은 서비스가 이 음수를 보고 판단한다(2.5) */
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("10", "200", "1300", "0", 1),
                    sell("15", "500", "1400", "0", 2)));

            assertThat(h.quantity()).isNegative();
        }

        @Test
        @DisplayName("거래가 없으면 전부 0 이다")
        void 거래없음() {
            Holding h = HoldingCalculator.calculate("AAPL", List.of());

            assertThat(h.quantity()).isEqualByComparingTo("0");
            assertThat(h.realizedPnlKrw()).isEqualByComparingTo("0");
            assertThat(h.held()).isFalse();
        }

        @Test
        @DisplayName("소수점 수량도 계산한다")
        void 소수점수량() {
            // ACCOUNT-03. (0.5×200 + 0.25×300) / 0.75 = 233.333333
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("0.5", "200", "1300", "0", 1),
                    buy("0.25", "300", "1300", "0", 2)));

            assertThat(h.quantity()).isEqualByComparingTo("0.75");
            assertThat(h.avgPrice()).isEqualByComparingTo("233.333333");
        }

        @Test
        @DisplayName("수수료는 매수·매도 모두 쌓인다")
        void 수수료누적() {
            Holding h = HoldingCalculator.calculate("AAPL", List.of(
                    buy("10", "200", "1300", "1.5", 1),
                    sell("4", "500", "1400", "2.5", 2)));

            assertThat(h.totalFee()).isEqualByComparingTo("4.0000");
        }
    }

    @Test
    @DisplayName("매입 원가는 수량 × 평단가")
    void 매입원가() {
        Holding h = HoldingCalculator.calculate("AAPL", List.of(
                buy("10", "200", "1300", "0", 1),
                buy("10", "300", "1300", "0", 2)));

        assertThat(h.costUsd()).isEqualByComparingTo("5000");
    }
}
