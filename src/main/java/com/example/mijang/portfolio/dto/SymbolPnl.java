/*
 * SymbolPnl — 종목 한 건의 계산 입력값
 *
 * 이 파일이 하는 일
 *   손익을 계산하려면 종목마다 수량·평단가·평균매수환율·현재가가 필요하다.
 *   그 네 값을 한 덩어리로 묶어 계산기에 넘기는 그릇이다.
 *   현재가가 비어 있으면 아직 일봉이 없는 종목이라 계산에서 뺀다.
 */
package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;

/**
 * 종목 한 건의 손익 분해 입력값.
 *
 * <p>{@code currentPrice} 가 null 이면 일봉이 아직 없는 종목이다. 계산에서 뺀다(2.5).
 */
public record SymbolPnl(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal avgFxRate,
        BigDecimal currentPrice) {

    /** 계산에 쓸 수 있는 값이 다 있는지. */
    public boolean calculable() {
        return currentPrice != null
                && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
    }
}
