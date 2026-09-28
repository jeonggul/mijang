package com.example.mijang.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 종목 상세의 투자 지표·기업 정보 응답이다. 모든 값이 null 일 수 있다. */
public record StockMetricsResponse(
        String symbol,
        BigDecimal marketCap,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal eps,
        BigDecimal dividendYield,
        BigDecimal beta,
        BigDecimal week52High,
        BigDecimal week52Low,
        String industry,
        String country,
        String webUrl,
        LocalDate ipoDate,
        String logoUrl,
        BigDecimal sharesOutstanding,
        LocalDateTime syncedAt) {
}
