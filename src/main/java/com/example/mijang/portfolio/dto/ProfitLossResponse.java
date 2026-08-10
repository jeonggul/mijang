package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;

/**
 * 손익 요인 분해. 개발명세서(API) PORT-002 — 이 서비스의 시그니처 기능이다.
 *
 * <pre>
 * 주가손익 = 수량 x (현재가 - 평단가) x 현재환율
 * 환차손익 = 수량 x 평단가 x (현재환율 - 평균매수환율)
 * </pre>
 */
public record ProfitLossResponse(
        BigDecimal totalValueKrw,
        BigDecimal pricePnlKrw,
        BigDecimal fxPnlKrw,
        BigDecimal totalPnlKrw,
        BigDecimal returnRate,
        BigDecimal appliedFxRate) {
}
