package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.dto.TransactionResponse;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.portfolio.service.LedgerService;
import com.example.mijang.portfolio.service.TaxService;
import com.example.mijang.stock.domain.StockSplit;
import com.example.mijang.support.TestLedger;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 분할 보정이 <b>모든 화면에서 같게</b> 적용되는가.
 *
 * <p>{@link StockSplitAdjustTest} 는 계산기가 분할을 옳게 보정하는지 본다. 여기서 보는 것은
 * 다른 문제다 — <b>그 보정을 모두가 쓰는가.</b>
 *
 * <p>2026-09-03 점검 4.1 에서 같은 계산을 부르는 네 곳 중 보유 현황만 분할을 넘기고 있었다.
 * 매매 기록 목록·CSV·양도세·커뮤니티 매매 카드는 분할을 무시해, 분할을 겪은 종목은 같은
 * 매도의 실현손익이 화면마다 다르게 나왔다. 계산기 시험은 전부 통과하고 있었다 —
 * 틀린 것은 계산이 아니라 <b>입력을 모으는 자리가 넷이라는 것</b>이었다.
 *
 * <p>그래서 운영 코드는 {@link LedgerService} 하나만 거치게 했고, 이 시험이 그것을 지킨다.
 */
class SplitConsistencyTest {

    private static final Long USER = 1L;
    private static final String SYM = "AAPL";

    /** 4:1 분할. 기준일은 2026-06-01. */
    private static final StockSplit SPLIT =
            new StockSplit(SYM, LocalDate.of(2026, 6, 1), "FORWARD",
                    BigDecimal.ONE, new BigDecimal("4"));

    /**
     * 분할 전에 사서 분할 후에 판 원장.
     *
     * <p>분할 전 10주 $400 은 지금 기준 40주 $100 이다. 분할 후 $120 에 40주를 팔면
     * 실현손익은 {@code 40 × ($120 − $100) × 1400 = 1,120,000원} 이다.
     *
     * <p>보정을 빠뜨리면 원가가 $400 로 잡혀 손익이 크게 음수로 나온다 — 오류가 아니라
     * 그냥 다른 숫자라 화면만 봐서는 어느 쪽이 맞는지 알 수 없다.
     */
    private static Ledger ledger() {
        Ledger ledger = new Ledger();
        ledger.add(tx(1L, "BUY", "10", "400", LocalDate.of(2026, 1, 10)));
        ledger.add(tx(2L, "SELL", "40", "120", LocalDate.of(2026, 7, 15)));
        return ledger;
    }

    private static LedgerService ledgerService(Ledger ledger) {
        return TestLedger.of(ledger, symbol -> List.of(SPLIT));
    }

    @Test
    @DisplayName("보유·실현손익 계산이 분할을 반영한다")
    void 계산이분할을반영한다() {
        var calc = ledgerService(ledger()).calculationOf(USER, SYM);

        assertThat(calc.holding().quantity()).isEqualByComparingTo("0");
        assertThat(calc.realizedBySellId().get(2L)).isEqualByComparingTo("1120000");
    }

    @Test
    @DisplayName("양도세 화면이 같은 실현손익을 쓴다")
    void 양도세가같은값을쓴다() {
        Ledger ledger = ledger();
        var expected = ledgerService(ledger).calculationOf(USER, SYM).realizedBySellId().get(2L);

        var tax = new TaxService(ledger, ledgerService(ledger)).capitalGains(USER, 2026);

        assertThat(tax.netKrw()).isEqualByComparingTo(expected);
        assertThat(tax.sellCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("분할을 빠뜨리면 다른 값이 나온다 — 이 시험이 무엇을 막는지 보이려고 둔다")
    void 분할을빠뜨리면갈린다() {
        Ledger ledger = ledger();
        var withSplit = ledgerService(ledger).calculationOf(USER, SYM).realizedBySellId().get(2L);
        var withoutSplit = TestLedger.of(ledger).calculationOf(USER, SYM).realizedBySellId().get(2L);

        assertThat(withSplit).isEqualByComparingTo("1120000");
        assertThat(withoutSplit).isNotEqualByComparingTo(withSplit);
    }

    private static Transaction tx(Long id, String side, String qty, String price, LocalDate date) {
        return new Transaction(id, USER, 1L, SYM, side,
                new BigDecimal(qty), new BigDecimal(price), new BigDecimal("1400"),
                BigDecimal.ZERO, date.atTime(16, 0), date, null, null, null);
    }

    /** 한 종목의 거래만 들고 있는 가짜 매퍼. 재계산용 조회만 진짜처럼 답한다. */
    private static class Ledger implements TransactionMapper {

        final List<Transaction> rows = new ArrayList<>();

        void add(Transaction tx) {
            rows.add(tx);
        }

        @Override public List<Transaction> findForRecalc(Long userId, String symbol) {
            return rows;
        }

        @Override public List<String> findSymbolsByUser(Long userId) {
            return List.of(SYM);
        }

        @Override public int insert(Long a, Long b, String c, String d, BigDecimal e,
                BigDecimal f, BigDecimal g, BigDecimal h, LocalDateTime i, LocalDate j,
                String k, BigDecimal l, String m) { return 0; }
        @Override public int update(Long id, Long userId, String symbol, String side,
                BigDecimal quantity, BigDecimal price, BigDecimal fxRate, BigDecimal fee,
                LocalDateTime tradedAt, LocalDate tradeDate, String buyReason,
                BigDecimal targetPrice, String sentiment) { return 0; }
        @Override public Long findLastInsertedId() { return null; }
        @Override public List<TransactionResponse> findByUser(Long u, String s, int l, int o) { return List.of(); }
        @Override public List<TransactionResponse> findForExport(
                Long u, String s, String side, LocalDate from, LocalDate to) { return List.of(); }
        @Override public long countByUser(Long u, String s) { return 0; }
        @Override public Transaction findById(Long id, Long u) { return null; }
        @Override public int softDelete(Long id, Long u) { return 0; }
    }
}
