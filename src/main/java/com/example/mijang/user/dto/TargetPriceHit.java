package com.example.mijang.user.dto;

import java.math.BigDecimal;

/** 목표가에 처음 닿은 보유 종목 한 건. NOTI-01 배치가 읽는다. */
public record TargetPriceHit(
        Long userId,
        String symbol,
        BigDecimal targetPrice,
        BigDecimal todayHigh) {
}
