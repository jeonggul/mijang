package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /** 건별 실현손익은 거래 id 로 돌아온다. 기본 헬퍼는 id 가 없으므로 붙여 준다. */
    private static Transaction withId(long id, Transaction t) {
        return new Transaction(id, t.userId(), t.portfolioId(), t.symbol(), t.side(),
                t.quantity(), t.price(), t.fxRate(), t.fee(), t.tradedAt(), t.tradeDate(),
                t.buyReason(), t.targetPrice(), t.sentiment());
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

        @Test
        @DisplayName("매도 건별로 그때의 원가를 함께 돌려준다")
        void 건별원가() {
            /* 커뮤니티 글에 붙는 매매 카드가 수익률을 내려면 나눌 원가가 필요하다.
               원가 = 수량 × 그 시점 평단가 × 평균매수환율 이고, 평단가는 뒤에 또 사면
               달라지므로 훑는 도중에 담아 두지 않으면 다시 구할 수 없다.
               4 × 200 × 1,300 = 1,040,000 원 */
            HoldingCalculator.Calculation c = HoldingCalculator.calculateAll("AAPL", List.of(
                    withId(1, buy("10", "200", "1300", "0", 1)),
                    withId(2, sell("4", "500", "1400", "0", 2)),
                    withId(3, buy("10", "400", "1300", "0", 3))));

            assertThat(c.costBasisBySellId().get(2L)).isEqualByComparingTo("1040000.00");
            // 나중 매수가 평단가를 올렸어도 앞선 매도의 원가는 그대로다
            assertThat(c.realizedBySellId().get(2L)).isEqualByComparingTo("1760000.00");
        }

        @Test
        @DisplayName("매수에는 원가가 담기지 않는다 — 처분한 몫이 없다")
        void 매수는원가없음() {
            HoldingCalculator.Calculation c = HoldingCalculator.calculateAll("AAPL", List.of(
                    withId(1, buy("10", "200", "1300", "0", 1))));

            assertThat(c.costBasisBySellId()).isEmpty();
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
        @DisplayName("최종 수량이 양수여도 도중에 보유를 넘겼으면 minQuantity 가 음수다")
        void 과거날짜_초과매도() {
            /* 2026-09-03 점검 3.2 에서 재현한 경로다. 8/10 매수보다 앞선 8/5 로 매도를
               끼워 넣으면 그 시점 보유는 0 인데, 최종 수량은 매수가 채워 줘 5 로 끝난다.
               최종 수량만 보는 검사는 이것을 통과시켰다 */
            HoldingCalculator.Calculation calc = HoldingCalculator.calculateAll("AAPL", List.of(
                    withId(1, sell("5", "200", "1300", "0", 5)),
                    withId(2, buy("10", "100", "1300", "0", 10))));

            assertThat(calc.holding().quantity()).isEqualByComparingTo("5");
            assertThat(calc.minQuantity()).isEqualByComparingTo("-5");
            assertThat(calc.oversold()).isTrue();
        }

        @Test
        @DisplayName("정상 순서로 사고팔면 minQuantity 가 0 아래로 내려가지 않는다")
        void 정상순서() {
            HoldingCalculator.Calculation calc = HoldingCalculator.calculateAll("AAPL", List.of(
                    withId(1, buy("10", "100", "1300", "0", 1)),
                    withId(2, sell("10", "200", "1300", "0", 2)),
                    withId(3, buy("3", "150", "1300", "0", 3))));

            assertThat(calc.minQuantity()).isEqualByComparingTo("0");
            assertThat(calc.oversold()).isFalse();
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

    @Nested
    @DisplayName("매도 건별 실현손익 — 화면 SR-007")
    class 건별실현손익 {

        /* 매수 10@$100(환율 1300) → 매도 5@$150(1400) → 매수 5@$200(1500) → 매도 4@$250(1600)
           두 번째 매도는 평단가가 150 으로 올라간 뒤라 첫 매도와 기준이 다르다.
           이 "그 시점 평단가" 때문에 거래 한 줄만 보고는 실현손익을 구할 수 없다. */
        @Test
        @DisplayName("매도마다 그 시점 평단가로 따로 계산된다")
        void 매도별로계산() {
            HoldingCalculator.Calculation c = HoldingCalculator.calculateAll("AAPL", List.of(
                    withId(1L, buy("10", "100", "1300", "0", 1)),
                    withId(2L, sell("5", "150", "1400", "0", 2)),
                    withId(3L, buy("5", "200", "1500", "0", 3)),
                    withId(4L, sell("4", "250", "1600", "0", 4))));

            // 5 × (150×1400 − 100×1300) = 5 × 80,000
            assertThat(c.realizedBySellId().get(2L)).isEqualByComparingTo("400000.00");
            // 평단 150 · 평균환율 1433.3333 기준 — 4 × (250×1600 − 150×1433.3333)
            assertThat(c.realizedBySellId().get(4L)).isEqualByComparingTo("740000.02");
        }

        @Test
        @DisplayName("건별 합이 보유 현황의 실현손익과 같다")
        void 합이일치한다() {
            HoldingCalculator.Calculation c = HoldingCalculator.calculateAll("AAPL", List.of(
                    withId(1L, buy("10", "100", "1300", "0", 1)),
                    withId(2L, sell("5", "150", "1400", "0", 2)),
                    withId(3L, buy("5", "200", "1500", "0", 3)),
                    withId(4L, sell("4", "250", "1600", "0", 4))));

            BigDecimal sum = c.realizedBySellId().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(c.holding().realizedPnlKrw());
        }

        @Test
        @DisplayName("매수는 들어 있지 않다")
        void 매수는제외() {
            HoldingCalculator.Calculation c = HoldingCalculator.calculateAll("AAPL", List.of(
                    withId(1L, buy("10", "100", "1300", "0", 1)),
                    withId(2L, sell("5", "150", "1400", "0", 2))));

            assertThat(c.realizedBySellId()).containsOnlyKeys(2L);
        }

        @Test
        @DisplayName("손실 매도는 음수로 담긴다")
        void 손실도담긴다() {
            HoldingCalculator.Calculation c = HoldingCalculator.calculateAll("AAPL", List.of(
                    withId(1L, buy("10", "200", "1300", "0", 1)),
                    withId(2L, sell("10", "100", "1300", "0", 2))));

            assertThat(c.realizedBySellId().get(2L)).isEqualByComparingTo("-1300000.00");
        }

        @Test
        @DisplayName("수수료를 뺀 값이 담긴다")
        void 수수료차감() {
            HoldingCalculator.Calculation c = HoldingCalculator.calculateAll("AAPL", List.of(
                    withId(1L, buy("10", "100", "1300", "0", 1)),
                    withId(2L, sell("5", "150", "1400", "5", 2))));

            // 400,000 − 5×1400
            assertThat(c.realizedBySellId().get(2L)).isEqualByComparingTo("393000.00");
        }

        /* 재계산 경로는 DB 에서 꺼낸 거래라 id 가 항상 있지만, 계산기는 id 없이도 부를 수 있어야
           한다 — 기존 테스트가 전부 그렇게 부른다. id 가 없으면 건별 값만 비고 합계는 그대로다. */
        @Test
        @DisplayName("id 가 없으면 건별 값은 비고 합계는 그대로다")
        void id없으면건별만비운다() {
            HoldingCalculator.Calculation c = HoldingCalculator.calculateAll("AAPL", List.of(
                    buy("10", "100", "1300", "0", 1),
                    sell("5", "150", "1400", "0", 2)));

            assertThat(c.realizedBySellId()).isEmpty();
            assertThat(c.holding().realizedPnlKrw()).isEqualByComparingTo("400000.00");
        }

        @Test
        @DisplayName("돌려준 지도는 고칠 수 없다")
        void 불변이다() {
            HoldingCalculator.Calculation c = HoldingCalculator.calculateAll("AAPL", List.of(
                    withId(1L, buy("10", "100", "1300", "0", 1)),
                    withId(2L, sell("5", "150", "1400", "0", 2))));

            assertThatThrownBy(() -> c.realizedBySellId().put(9L, BigDecimal.ONE))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
