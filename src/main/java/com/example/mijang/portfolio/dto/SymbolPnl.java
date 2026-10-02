package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;

/** 종목 한 건의 손익 분해 입력값이다. currentPrice 가 null 이면 계산에서 뺀다. */
public record SymbolPnl(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal avgFxRate,
        BigDecimal currentPrice) {

    /** 손익 계산에 필요한 값이 다 있는지 판별한다. */
    public boolean calculable() {
        return currentPrice != null
                && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
    }
}
