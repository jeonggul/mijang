package com.example.mijang.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 매매 원장(transactions)의 한 행이다. 판단 메모(사유·목표가·심리)를 함께 담는다. */
public record Transaction(
        Long id,
        Long userId,
        Long portfolioId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fxRate,
        BigDecimal fee,
        LocalDateTime tradedAt,
        LocalDate tradeDate,
        String buyReason,
        BigDecimal targetPrice,
        String sentiment) {

    /** 매수 거래인지 판별한다. */
    public boolean buy() {
        return "BUY".equals(side);
    }

    /** 수수료를 제외한 체결 금액(USD)을 계산한다. */
    public BigDecimal amountUsd() {
        return quantity.multiply(price);
    }
}
