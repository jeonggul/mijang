package com.example.mijang.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 일봉 한 건. 개발명세서(API) PRICE-05 · PK 는 (티커, 거래일) */
public record CandleResponse(
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {
}
