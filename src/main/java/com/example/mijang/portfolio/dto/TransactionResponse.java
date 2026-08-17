/*
 * TransactionResponse — 매매 기록 응답
 *
 * 이 파일이 하는 일
 *   거래 목록 화면에 한 줄로 뜨는 값이다.
 *   종목명을 함께 담는다 — 티커만 주면 화면이 종목마다 이름을 다시 물어야 해서
 *   20건이면 21번 요청이 된다.
 */
package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 매매 기록 한 건. 개발명세서(API) ACCOUNT-06
 *
 * <p>종목명을 함께 담는다. 목록 화면이 티커만 받으면 종목마다 이름을 다시 물어야 한다.
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
        String sentiment) {
}
