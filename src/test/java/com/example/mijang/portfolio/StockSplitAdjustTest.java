package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.portfolio.domain.Holding;
import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.service.HoldingCalculator;
import com.example.mijang.stock.domain.StockSplit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 주식 분할 보정.
 *
 * <p>이 시험이 있는 이유 — 시세는 분할이 반영된 값으로 들어오는데(bars 를
 * {@code adjustment=split} 으로 받는다) 매매 기록은 그날 체결한 그대로다.
 * 보정하지 않으면 4:1 분할 뒤 평가금액이 4분의 1로 보인다. 화면에는 오류가 아니라
 * <b>그냥 틀린 숫자</b>로 나타나서, 시험이 없으면 아무도 모른다.
 *
 * <p>여기서 보려는 것은 넷이다 — <b>분할 전 거래가 환산되는가</b>,
 * <b>기준일 당일 거래는 그대로 두는가</b>, <b>여러 번 분할해도 누적되는가</b>,
 * <b>역분할도 같은 식으로 도는가</b>.
 */
class StockSplitAdjustTest {

    private static final String SYM = "AAPL";

    private static Transaction buy(LocalDate date, String qty, String price) {
        return new Transaction(1L, 1L, 1L, SYM, "BUY",
                new BigDecimal(qty), new BigDecimal(price),
                new BigDecimal("1400"), BigDecimal.ZERO,
                date.atTime(16, 0), date, null, null, null);
    }

    private static StockSplit split(LocalDate exDate, String oldRate, String newRate) {
        return new StockSplit(SYM, exDate, "FORWARD",
                new BigDecimal(oldRate), new BigDecimal(newRate));
    }

    @Nested
    @DisplayName("정분할")
    class 정분할 {

        /* 4:1 분할 전 10주 $400 은 지금 기준 40주 $100 이다. 쓴 돈은 그대로 $4,000 */
        @Test
        @DisplayName("분할 전 거래는 지금 기준으로 환산된다")
        void 분할전거래() {
            List<Transaction> txs = List.of(buy(LocalDate.of(2026, 1, 10), "10", "400"));
            List<StockSplit> splits = List.of(split(LocalDate.of(2026, 6, 1), "1", "4"));

            Holding holding = HoldingCalculator.calculate(SYM, txs, splits);

            assertThat(holding.quantity()).isEqualByComparingTo("40");
            assertThat(holding.avgPrice()).isEqualByComparingTo("100");
        }

        /* 기준일부터는 이미 조정된 값으로 체결된다. 여기서 또 보정하면 두 번 곱해진다 */
        @Test
        @DisplayName("기준일 당일 거래는 건드리지 않는다")
        void 기준일당일() {
            LocalDate ex = LocalDate.of(2026, 6, 1);
            List<Transaction> txs = List.of(buy(ex, "40", "100"));

            Holding holding = HoldingCalculator.calculate(SYM, txs, List.of(split(ex, "1", "4")));

            assertThat(holding.quantity()).isEqualByComparingTo("40");
            assertThat(holding.avgPrice()).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("분할 이후 거래도 건드리지 않는다")
        void 분할이후() {
            List<Transaction> txs = List.of(buy(LocalDate.of(2026, 7, 1), "5", "120"));

            Holding holding = HoldingCalculator.calculate(SYM, txs,
                    List.of(split(LocalDate.of(2026, 6, 1), "1", "4")));

            assertThat(holding.quantity()).isEqualByComparingTo("5");
            assertThat(holding.avgPrice()).isEqualByComparingTo("120");
        }

        /* 분할을 사이에 두고 산 두 건이 같은 기준 위에서 평균이 나야 한다 */
        @Test
        @DisplayName("분할 전후 거래가 한 평단으로 합쳐진다")
        void 분할전후혼합() {
            List<Transaction> txs = List.of(
                    buy(LocalDate.of(2026, 1, 10), "10", "400"),   // → 40주 $100
                    buy(LocalDate.of(2026, 7, 1), "10", "150"));   // 그대로 10주 $150
            List<StockSplit> splits = List.of(split(LocalDate.of(2026, 6, 1), "1", "4"));

            Holding holding = HoldingCalculator.calculate(SYM, txs, splits);

            assertThat(holding.quantity()).isEqualByComparingTo("50");
            // (40×100 + 10×150) / 50 = 110
            assertThat(holding.avgPrice()).isEqualByComparingTo("110");
        }

        /* 두 번 분할한 종목을 한 번만 보정하면 수량이 절반으로 남는다 */
        @Test
        @DisplayName("분할이 두 번이면 배수가 누적된다")
        void 두번분할() {
            List<Transaction> txs = List.of(buy(LocalDate.of(2026, 1, 10), "10", "400"));
            List<StockSplit> splits = List.of(
                    split(LocalDate.of(2026, 3, 1), "1", "2"),
                    split(LocalDate.of(2026, 6, 1), "1", "4"));

            Holding holding = HoldingCalculator.calculate(SYM, txs, splits);

            assertThat(holding.quantity()).isEqualByComparingTo("80");   // 10 × 2 × 4
            assertThat(holding.avgPrice()).isEqualByComparingTo("50");   // 400 ÷ 8
        }

        /* 두 분할 사이에 산 건은 뒤쪽 분할만 먹어야 한다 */
        @Test
        @DisplayName("두 분할 사이 거래는 뒤쪽만 반영한다")
        void 분할사이거래() {
            List<Transaction> txs = List.of(buy(LocalDate.of(2026, 4, 1), "10", "400"));
            List<StockSplit> splits = List.of(
                    split(LocalDate.of(2026, 3, 1), "1", "2"),
                    split(LocalDate.of(2026, 6, 1), "1", "4"));

            Holding holding = HoldingCalculator.calculate(SYM, txs, splits);

            assertThat(holding.quantity()).isEqualByComparingTo("40");
            assertThat(holding.avgPrice()).isEqualByComparingTo("100");
        }
    }

    @Nested
    @DisplayName("역분할")
    class 역분할 {

        /* 1:10 병합이면 100주가 10주가 되고 단가는 열 배가 된다 */
        @Test
        @DisplayName("주식 병합도 같은 식으로 환산된다")
        void 병합() {
            List<Transaction> txs = List.of(buy(LocalDate.of(2026, 1, 10), "100", "3"));
            List<StockSplit> splits = List.of(
                    new StockSplit(SYM, LocalDate.of(2026, 6, 1), "REVERSE",
                            new BigDecimal("10"), new BigDecimal("1")));

            Holding holding = HoldingCalculator.calculate(SYM, txs, splits);

            assertThat(holding.quantity()).isEqualByComparingTo("10");
            assertThat(holding.avgPrice()).isEqualByComparingTo("30");
        }
    }

    @Nested
    @DisplayName("경계")
    class 경계 {

        @Test
        @DisplayName("분할이 없으면 아무것도 바뀌지 않는다")
        void 분할없음() {
            List<Transaction> txs = List.of(buy(LocalDate.of(2026, 1, 10), "10", "400"));

            Holding 그냥 = HoldingCalculator.calculate(SYM, txs);
            Holding 빈분할 = HoldingCalculator.calculate(SYM, txs, List.of());

            assertThat(빈분할.quantity()).isEqualByComparingTo(그냥.quantity());
            assertThat(빈분할.avgPrice()).isEqualByComparingTo(그냥.avgPrice());
        }

        /* 비율이 깨진 행이 들어와도 보유가 0 이나 무한이 되면 안 된다 */
        @Test
        @DisplayName("비율이 0 이면 보정하지 않는다")
        void 깨진비율() {
            List<Transaction> txs = List.of(buy(LocalDate.of(2026, 1, 10), "10", "400"));
            List<StockSplit> splits = List.of(split(LocalDate.of(2026, 6, 1), "0", "4"));

            Holding holding = HoldingCalculator.calculate(SYM, txs, splits);

            assertThat(holding.quantity()).isEqualByComparingTo("10");
            assertThat(holding.avgPrice()).isEqualByComparingTo("400");
        }

        /* 나누어떨어지지 않는 비율에서 수량이 튀면 안 된다 */
        @Test
        @DisplayName("3:2 분할처럼 딱 떨어지지 않아도 총 금액이 보존된다")
        void 소수배수() {
            List<Transaction> txs = List.of(buy(LocalDate.of(2026, 1, 10), "10", "300"));
            List<StockSplit> splits = List.of(split(LocalDate.of(2026, 6, 1), "2", "3"));

            Holding holding = HoldingCalculator.calculate(SYM, txs, splits);

            assertThat(holding.quantity()).isEqualByComparingTo("15");
            assertThat(holding.avgPrice()).isEqualByComparingTo("200");
            // 15 × 200 = 3,000 — 분할 전 10 × 300 과 같다
            assertThat(holding.quantity().multiply(holding.avgPrice()))
                    .isEqualByComparingTo("3000");
        }

        @Test
        @DisplayName("매도 뒤에 분할이 있어도 실현손익은 그때 값 그대로다")
        void 매도후분할() {
            Transaction sell = new Transaction(2L, 1L, 1L, SYM, "SELL",
                    new BigDecimal("5"), new BigDecimal("500"),
                    new BigDecimal("1400"), BigDecimal.ZERO,
                    LocalDateTime.of(2026, 2, 1, 16, 0), LocalDate.of(2026, 2, 1),
                    null, null, null);
            List<Transaction> txs = List.of(buy(LocalDate.of(2026, 1, 10), "10", "400"), sell);
            List<StockSplit> splits = List.of(split(LocalDate.of(2026, 6, 1), "1", "4"));

            Holding holding = HoldingCalculator.calculate(SYM, txs, splits);

            /* 남은 5주가 4배가 되어 20주. 판 몫도 같은 배수로 환산돼 계산이 어긋나지 않는다 */
            assertThat(holding.quantity()).isEqualByComparingTo("20");
        }
    }
}
