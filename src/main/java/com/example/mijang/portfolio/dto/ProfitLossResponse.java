/*
 * ProfitLossResponse — 손익 분해 응답
 *
 * 이 파일이 하는 일
 *   대시보드가 받아 가는 손익 한 덩어리다.
 *   이 서비스의 간판 기능이 여기 담긴다 — 번 돈을 "주가가 올라서 번 것"과
 *   "환율이 올라서 번 것"으로 갈라서 보여 준다. 원화와 달러 값을 둘 다 담고,
 *   현재가가 없어 계산에서 빠진 종목 수도 같이 알려 준다.
 */
package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 손익 요인 분해. 개발명세서(API) PROFIT-03 — <b>이 서비스의 시그니처 기능이다.</b>
 *
 * <pre>
 * 주가손익 = 수량 × (현재가 − 평단가) × 현재환율
 * 환차손익 = 수량 × 평단가 × (현재환율 − 평균매수환율)
 * 합계     = 주가손익 + 환차손익
 * </pre>
 *
 * <p>원화와 달러 값을 <b>둘 다 담는다</b>. 달러 기준에는 환차손익이 없다(2.7).
 *
 * <p>{@code skippedSymbols} 는 현재가가 없어 계산에서 빠진 종목 수다. 조용히 빼면
 * 사용자는 합계가 왜 작은지 알 수 없다(2.5).
 */
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
