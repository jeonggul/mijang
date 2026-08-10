package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;

/**
 * 보유 현황 한 건. 개발명세서(API) PORT-001
 *
 * <p>보유 수량·평균단가·평균매수환율은 매매 기록에서 계산되는 파생값이다. 원장은 transactions.
 */
public record HoldingResponse(
        String symbol,
        String name,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal avgFxRate,
        BigDecimal currentPrice,
        BigDecimal marketValueKrw) {
}
