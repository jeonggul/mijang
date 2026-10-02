package com.example.mijang.support;

import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.portfolio.service.LedgerService;
import com.example.mijang.stock.domain.StockSplit;
import com.example.mijang.stock.mapper.StockSplitMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 시험용 {@link LedgerService}.
 *
 * <p>운영 코드는 계산기 입력을 전부 LedgerService 를 거쳐 모은다(2026-09-03 점검 4.1).
 * 그래서 그 계산을 쓰는 서비스 시험은 하나씩 이것을 세워야 하는데, 매번 분할 매퍼를
 * 흉내 내는 코드를 적으면 시험마다 조금씩 달라진다. 여기 한 곳에 둔다.
 */
public final class TestLedger {

    private TestLedger() {
    }

    /** 분할이 없는 원장. 대부분의 시험은 분할과 무관하다. */
    public static LedgerService of(TransactionMapper transactions) {
        return of(transactions, symbol -> List.of());
    }

    /** 분할을 지정한 원장. 분할 보정을 확인하는 시험이 쓴다. */
    public static LedgerService of(TransactionMapper transactions, Splits splits) {
        return new LedgerService(transactions, new StockSplitMapper() {
            @Override public List<StockSplit> findBySymbol(String symbol) {
                return splits.of(symbol);
            }

            @Override public int insertIgnore(String symbol, LocalDate exDate, String splitType,
                                              BigDecimal oldRate, BigDecimal newRate,
                                              String vendorId) {
                return 0;
            }

            @Override public List<String> findHeldSymbols() { return List.of(); }

            @Override public long count() { return 0; }
        });
    }

    /** 종목 → 분할 목록. */
    @FunctionalInterface
    public interface Splits {
        List<StockSplit> of(String symbol);
    }
}
