/*
 * TransactionResponse — 매매 기록 응답
 *
 * 이 파일이 하는 일
 *   거래 목록 화면에 한 줄로 뜨는 값이다.
 *   종목명을 함께 담는다 — 티커만 주면 화면이 종목마다 이름을 다시 물어야 해서
 *   20건이면 21번 요청이 된다.
 *   매도에는 그 매도가 확정한 실현손익이 붙는다. 그 값은 그 시점의 평단가에 달려 있어
 *   화면에서 만들 수 없다.
 */
package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 매매 기록 한 건. 개발명세서(API) ACCOUNT-06
 *
 * <p>종목명을 함께 담는다. 목록 화면이 티커만 받으면 종목마다 이름을 다시 물어야 한다.
 *
 * <p>{@code realizedPnlKrw} 는 <b>매도에만</b> 붙는다(화면 SR-007). 매수는 null 이다.
 * 원장에 저장된 값이 아니라 조회할 때 계산된 파생값이다(2.1) — 과거 날짜를 나중에 끼워 넣으면
 * 그 뒤 매도들의 실현손익이 전부 달라지므로 저장해 두면 그 순간 틀린 값이 된다.
 */
public record TransactionResponse(
        Long id,
        String symbol,
        String name,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fxRate,
        BigDecimal fee,
        LocalDateTime tradedAt,
        LocalDate tradeDate,
        String buyReason,
        BigDecimal targetPrice,
        String sentiment,
        BigDecimal realizedPnlKrw) {

    /** 실현손익을 채워 넣은 사본. record 라 값을 바꾸는 대신 새로 만든다. */
    public TransactionResponse withRealizedPnlKrw(BigDecimal realized) {
        return new TransactionResponse(id, symbol, name, side, quantity, price, fxRate, fee,
                tradedAt, tradeDate, buyReason, targetPrice, sentiment, realized);
    }
}
