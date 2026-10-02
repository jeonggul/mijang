/*
 * Holding — 보유 현황 한 종목
 *
 * 이 파일이 하는 일
 *   지금 무엇을 얼마나 들고 있는지를 담는다.
 *   사용자가 직접 적는 값이 아니라 매매 기록에서 계산되어 나오는 값이다.
 *   수량이 0 이 되어도 지우지 않는다 — 지우면 그동안 확정된 실현손익이 같이 사라진다.
 */
package com.example.mijang.portfolio.domain;

import java.math.BigDecimal;

/**
 * 보유 현황 한 종목. <b>원장에서 계산된 파생값</b>이다(2.1).
 *
 * <p>{@code quantity} 가 0 이어도 남긴다. 지우면 실현손익이 함께 사라진다(2.4).
 */
public record Holding(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal avgFxRate,
        BigDecimal totalFee,
        BigDecimal realizedPnlKrw) {

    /** 지금 들고 있는 종목인지. 화면은 이 값이 true 인 것만 보여준다. */
    public boolean held() {
        return quantity.compareTo(BigDecimal.ZERO) > 0;
    }

    /** 매입 원가(USD). 손익 분해의 기준이 된다. */
    public BigDecimal costUsd() {
        return quantity.multiply(avgPrice);
    }
}
