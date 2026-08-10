package com.example.mijang.stock.dto;

import java.math.BigDecimal;

/**
 * 종목 상세. 개발명세서(API) STOCK-002
 *
 * <p>현재가는 IEX 실시간(표시용)이고 손익 계산은 SIP 일봉 종가를 쓴다. 두 값은 다를 수 있다.
 */
public record StockDetailResponse(
        String symbol,
        String name,
        String exchange,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal dayChangeRate) {
}
