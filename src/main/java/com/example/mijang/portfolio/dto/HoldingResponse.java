/*
 * HoldingResponse — 보유 현황 응답
 *
 * 이 파일이 하는 일
 *   포트폴리오 화면의 표 한 줄이다.
 *   보유 수량·평단가·평균매수환율은 매매 기록에서 계산된 값이고,
 *   현재가는 마지막 일봉 종가다.
 *   환율을 못 구하면 평가금액과 평가손익이 비어서 나간다 — 원화로 바꿀 수가 없기 때문이다.
 */
package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;

/**
 * 보유 현황 한 건. 개발명세서(API) ACCOUNT-04·ACCOUNT-05
 *
 * <p>보유 수량·평균단가·평균매수환율은 매매 기록에서 계산되는 파생값이다. 원장은 transactions(2.1).
 *
 * <p>{@code currentPrice} 는 마지막 일봉 종가다. 실시간은 {@code market} 범위에서 붙는다.
 * {@code marketValueKrw}·{@code evalPnlKrw} 는 환율이 없으면 null 이다.
 */
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
        BigDecimal evalPnlKrw) {
}
