package com.example.mijang.stock.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/** 주식 분할 한 건 — stock_splits 한 행이다. exDate(기준일)부터 조정된 수량·주가로 거래된다. */
public record StockSplit(String symbol,
                         LocalDate exDate,
                         String splitType,
                         BigDecimal oldRate,
                         BigDecimal newRate) {

    /** 보정 배수(분할 전 1주가 몇 주가 되었는가)를 돌려준다. 비율이 깨져 있으면 1 이다. */
    public BigDecimal factor() {
        if (oldRate == null || newRate == null
                || oldRate.signum() <= 0 || newRate.signum() <= 0) {
            return BigDecimal.ONE;
        }
        return newRate.divide(oldRate, MathContext.DECIMAL64);
    }
}
