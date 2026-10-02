package com.example.mijang.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 관심종목 한 건의 응답이다. 시세를 함께 담는다. */
public record WatchlistItemResponse(
        Long id,
        String symbol,
        String name,
        String nameKo,
        boolean active,
        BigDecimal currentPrice,
        BigDecimal dayChangeRate,
        LocalDate asOf) {
}
