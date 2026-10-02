package com.example.mijang.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 예상 배당 지급 예정 판정 한 건이다. */
public record DividendPayHit(
        Long userId,
        String symbol,
        LocalDate payDate,
        BigDecimal netAmountUsd) {
}
