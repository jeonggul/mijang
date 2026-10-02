package com.example.mijang.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** 화면으로 나가는 현재가 한 건이다. live 가 false 면 마지막 종가, delayed 는 15분 지연 값을 뜻한다. */
public record QuoteResponse(
        String symbol,
        BigDecimal price,
        Instant at,
        boolean live,
        boolean delayed) {
}
