package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 매매 기록 한 건의 응답이다. realizedPnlKrw 는 매도에만 붙는 조회 시점 파생값이다. */
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

    /** 실현손익을 채워 넣은 사본을 만든다. */
    public TransactionResponse withRealizedPnlKrw(BigDecimal realized) {
        return new TransactionResponse(id, symbol, name, side, quantity, price, fxRate, fee,
                tradedAt, tradeDate, buyReason, targetPrice, sentiment, realized);
    }
}
