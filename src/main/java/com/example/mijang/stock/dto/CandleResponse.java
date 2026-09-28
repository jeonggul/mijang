package com.example.mijang.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 일봉 한 건의 응답이다. */
public record CandleResponse(
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {
}
