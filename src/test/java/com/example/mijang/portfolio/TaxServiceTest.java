package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.dto.CapitalGainsResponse;
import com.example.mijang.portfolio.dto.TransactionResponse;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.portfolio.service.TaxService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 양도소득세 참고 계산.
 *
 * <p>DB 를 부르지 않는다 — 여기서 보려는 것은 <b>연도로 묶고 공제를 적용하는
 * 규칙</b>뿐이고, 건별 실현손익 자체는 HoldingCalculatorTest 가 이미 지킨다.
 */
class TaxServiceTest {

    private static final Long USER = 1L;

    /** 종목별 거래 목록을 들고 있는 가짜 매퍼. 재계산용 조회만 진짜처럼 답한다. */
    private static class Ledger implements TransactionMapper {
        @Override public int update(Long id, Long userId, String symbol, String side,
                                    java.math.BigDecimal quantity, java.math.BigDecimal price,
                                    java.math.BigDecimal fxRate, java.math.BigDecimal fee,
                                    java.time.LocalDateTime tradedAt, java.time.LocalDate tradeDate,
                                    String buyReason, java.math.BigDecimal targetPrice,
                                    String sentiment) { return 1; }

        final Map<String, List<Transaction>> bySymbol = new LinkedHashMap<>();

        void add(Transaction tx) {
            bySymbol.computeIfAbsent(tx.symbol(), k -> new ArrayList<>()).add(tx);
        }

        @Override public List<String> findSymbolsByUser(Long userId) {
            return new ArrayList<>(bySymbol.keySet());
        }
        @Override public List<Transaction> findForRecalc(Long userId, String symbol) {
            return bySymbol.getOrDefault(symbol, List.of());
        }
        @Override public int insert(Long a, Long b, String c, String d, BigDecimal e,
                BigDecimal f, BigDecimal g, BigDecimal h, LocalDateTime i, LocalDate j,
                String k, BigDecimal l, String m) { return 0; }
        @Override public Long findLastInsertedId() { return null; }
        @Override public List<TransactionResponse> findByUser(Long u, String s, int l, int o) { return List.of(); }
        @Override public List<TransactionResponse> findForExport(
                Long u, String s, String side, LocalDate from, LocalDate to) { return List.of(); }
        @Override public long countByUser(Long u, String s) { return 0; }
        @Override public Transaction findById(Long id, Long u) { return null; }
        @Override public int softDelete(Long id, Long u) { return 0; }
    }

    private static long seq = 0;

    /** 판단 메모 없는 거래 한 건. 세금 계산에는 수량·단가·환율·날짜만 쓰인다. */
    private static Transaction tx(String symbol, String side, String qty, String price,
                                  String fx, LocalDate date) {
        return new Transaction(++seq, USER, 1L, symbol, side,
                new BigDecimal(qty), new BigDecimal(price), new BigDecimal(fx),
                BigDecimal.ZERO, date.atStartOfDay(), date, null, null, null);
    }

    @Test
    @DisplayName("이익과 손실을 따로 모으고 통산한다")
    void 이익과손실을가른다() {
        Ledger ledger = new Ledger();
        // AAPL — $100·1400 에 10주 사서 $150·1400 에 5주 매도 → +350,000원 이익
        ledger.add(tx("AAPL", "BUY", "10", "100", "1400", LocalDate.of(2026, 1, 5)));
        ledger.add(tx("AAPL", "SELL", "5", "150", "1400", LocalDate.of(2026, 3, 10)));
        // TSLA — $200·1400 에 5주 사서 $180·1400 에 5주 매도 → −140,000원 손실
        ledger.add(tx("TSLA", "BUY", "5", "200", "1400", LocalDate.of(2026, 2, 1)));
        ledger.add(tx("TSLA", "SELL", "5", "180", "1400", LocalDate.of(2026, 4, 1)));

        CapitalGainsResponse out = new TaxService(ledger).capitalGains(USER, 2026);

        assertThat(out.sellCount()).isEqualTo(2);
        assertThat(out.gainKrw()).isEqualByComparingTo("350000");
        assertThat(out.lossKrw()).isEqualByComparingTo("-140000");
        assertThat(out.netKrw()).isEqualByComparingTo("210000");
    }

    @Test
    @DisplayName("통산이 기본공제보다 작으면 과세표준과 세액은 0이다")
    void 공제아래는세액이없다() {
        Ledger ledger = new Ledger();
        ledger.add(tx("AAPL", "BUY", "10", "100", "1400", LocalDate.of(2026, 1, 5)));
        ledger.add(tx("AAPL", "SELL", "5", "150", "1400", LocalDate.of(2026, 3, 10)));

        CapitalGainsResponse out = new TaxService(ledger).capitalGains(USER, 2026);

        assertThat(out.taxableKrw()).isEqualByComparingTo("0");
        assertThat(out.estimatedTaxKrw()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("공제를 넘는 통산에는 22%를 적용한다")
    void 공제를넘으면세액이붙는다() {
        Ledger ledger = new Ledger();
        // +7,000,000원 이익 → 공제 250만 빼면 450만 → 22% = 990,000원
        ledger.add(tx("NVDA", "BUY", "10", "100", "1400", LocalDate.of(2026, 1, 5)));
        ledger.add(tx("NVDA", "SELL", "10", "600", "1400", LocalDate.of(2026, 6, 1)));

        CapitalGainsResponse out = new TaxService(ledger).capitalGains(USER, 2026);

        assertThat(out.netKrw()).isEqualByComparingTo("7000000");
        assertThat(out.taxableKrw()).isEqualByComparingTo("4500000");
        assertThat(out.estimatedTaxKrw()).isEqualByComparingTo("990000");
    }

    @Test
    @DisplayName("고른 해의 매도만 들어간다 — 다른 해는 연도 목록으로만 보인다")
    void 연도로자른다() {
        Ledger ledger = new Ledger();
        ledger.add(tx("AAPL", "BUY", "10", "100", "1400", LocalDate.of(2025, 1, 5)));
        ledger.add(tx("AAPL", "SELL", "5", "150", "1400", LocalDate.of(2025, 3, 10)));
        ledger.add(tx("AAPL", "SELL", "5", "160", "1400", LocalDate.of(2026, 2, 10)));

        CapitalGainsResponse out = new TaxService(ledger).capitalGains(USER, 2025);

        assertThat(out.sellCount()).isEqualTo(1);
        assertThat(out.gainKrw()).isEqualByComparingTo("350000");
        assertThat(out.availableYears()).containsExactly(2026, 2025);
    }

    @Test
    @DisplayName("연도를 비우면 매도가 있는 가장 최근 해로 답한다")
    void 연도를비우면최근해다() {
        Ledger ledger = new Ledger();
        ledger.add(tx("AAPL", "BUY", "10", "100", "1400", LocalDate.of(2024, 1, 5)));
        ledger.add(tx("AAPL", "SELL", "5", "150", "1400", LocalDate.of(2025, 3, 10)));

        CapitalGainsResponse out = new TaxService(ledger).capitalGains(USER, null);

        assertThat(out.year()).isEqualTo(2025);
        assertThat(out.sellCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("매도가 없으면 전부 0이다")
    void 매도가없으면빈값이다() {
        CapitalGainsResponse out = new TaxService(new Ledger()).capitalGains(USER, 2026);

        assertThat(out.sellCount()).isZero();
        assertThat(out.netKrw()).isEqualByComparingTo("0");
        assertThat(out.estimatedTaxKrw()).isEqualByComparingTo("0");
        assertThat(out.availableYears()).isEmpty();
    }

    @Test
    @DisplayName("과거 날짜를 끼워 넣어도 건별 실현손익은 그 시점 평단가를 따른다")
    void 실현손익은평단가를따른다() {
        Ledger ledger = new Ledger();
        // $100 에 10주 → $200 에 10주 더 (평단 $150) → $180 에 10주 매도 → +420,000원
        ledger.add(tx("MSFT", "BUY", "10", "100", "1400", LocalDate.of(2026, 1, 5)));
        ledger.add(tx("MSFT", "BUY", "10", "200", "1400", LocalDate.of(2026, 2, 5)));
        ledger.add(tx("MSFT", "SELL", "10", "180", "1400", LocalDate.of(2026, 3, 5)));

        CapitalGainsResponse out = new TaxService(ledger).capitalGains(USER, 2026);

        assertThat(out.gainKrw()).isEqualByComparingTo("420000");
    }
}
