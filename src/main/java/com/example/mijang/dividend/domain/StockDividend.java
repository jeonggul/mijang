package com.example.mijang.dividend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** stock_dividends 한 행(종목 자체의 배당 이벤트)을 나타낸다. special 은 연간 추정에서 제외한다. PROFIT-12 · INFO-06. */
public record StockDividend(
        String symbol,
        LocalDate exDate,
        String dividendType,
        BigDecimal amountPerShare,
        LocalDate recordDate,
        LocalDate payableDate,
        LocalDate processDate,
        boolean special,
        boolean foreign,
        String cusip,
        String vendorId) {
}
