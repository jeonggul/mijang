/*
 * Transaction — 매매 기록 한 건
 *
 * 이 파일이 하는 일
 *   transactions 테이블의 한 행이다. 이 표가 원장이고, 나머지 숫자는
 *   전부 여기서 계산되어 나온다.
 *   수량·단가·환율 말고 판단 메모(왜 샀는지·목표가·그때 심리)가 함께 들어 있는 것이
 *   이 서비스의 차별점이다. 다른 서비스는 얼마에 몇 주 샀는지만 남는다.
 */
package com.example.mijang.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * transactions 한 행. <b>이 표가 원장이다</b>(2.1).
 *
 * <p>판단 메모(사유·목표가·심리)가 함께 들어 있는 것이 이 서비스의 차별점이다.
 * 다른 서비스는 수량과 단가만 남기고, 왜 샀는지는 남지 않는다.
 */
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

    /** 매수인지. 재계산이 갈래를 나눌 때 쓴다. */
    public boolean buy() {
        return "BUY".equals(side);
    }

    /** 이 거래의 체결 금액(USD). 수수료는 빼고 본다. */
    public BigDecimal amountUsd() {
        return quantity.multiply(price);
    }
}
