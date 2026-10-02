package com.example.mijang.dividend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** dividends 한 행을 나타낸다. status 가 ESTIMATED 면 손익 집계에서 제외된다. PROFIT-11·12 · SR-016. */
public record Dividend(
        Long id,
        Long userId,
        Long portfolioId,
        String symbol,
        LocalDate exDate,
        LocalDate payDate,
        BigDecimal amountPerShare,
        BigDecimal quantityAtExDate,
        BigDecimal grossAmountUsd,
        BigDecimal netAmountUsd,
        BigDecimal withholdingRate,
        BigDecimal fxRate,
        BigDecimal netAmountKrw,
        String status,
        String source,
        LocalDateTime confirmedAt) {

    /** 일반 주식 기준 원천징수율. REIT·MLP 는 다를 수 있다. */
    public static final BigDecimal DEFAULT_WITHHOLDING = new BigDecimal("0.15");

    /** 확정 상태인지 반환한다. */
    public boolean confirmed() {
        return "CONFIRMED".equals(status);
    }
}
