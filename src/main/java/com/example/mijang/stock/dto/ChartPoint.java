package com.example.mijang.stock.dto;

import java.math.BigDecimal;

/** 차트의 봉 하나다. at 은 일봉이면 날짜, 분봉이면 시각 문자열이다. */
public record ChartPoint(
        String at,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {
}
