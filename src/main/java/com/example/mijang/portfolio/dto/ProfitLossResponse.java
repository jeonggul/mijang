package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 손익을 주가손익과 환차손익으로 분해한 응답이다. 주가손익 = 수량 × (현재가 − 평단가) × 현재환율, 환차손익 = 수량 × 평단가 × (현재환율 − 평균매수환율). */
public record ProfitLossResponse(
        LocalDate asOf,
        BigDecimal totalValueKrw,
        BigDecimal totalValueUsd,
        BigDecimal costBasisKrw,
        BigDecimal pricePnlKrw,
        BigDecimal fxPnlKrw,
        BigDecimal totalPnlKrw,
        BigDecimal pricePnlUsd,
        BigDecimal totalPnlUsd,
        BigDecimal returnRate,
        String state,
        BigDecimal appliedFxRate,
        boolean fxSubstituted,
        int skippedSymbols) {
}
