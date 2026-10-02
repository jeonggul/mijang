package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;

/** 보유 현황 한 건의 응답이다. 환율이 없으면 평가금액·평가손익은 null 이다. */
public record HoldingResponse(
        String symbol,
        String name,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal avgFxRate,
        BigDecimal totalFee,
        BigDecimal realizedPnlKrw,
        BigDecimal currentPrice,
        BigDecimal marketValueKrw,
        BigDecimal evalPnlKrw,
        // 평가손익 분해. ProfitLossCalculator 와 같은 식이어야 합계가 맞는다
        BigDecimal pricePnlKrw,
        BigDecimal fxPnlKrw,
        // 직전 종가 대비 등락률(%)
        BigDecimal dayChangeRate) {
}
