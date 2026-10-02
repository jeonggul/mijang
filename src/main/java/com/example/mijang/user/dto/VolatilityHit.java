package com.example.mijang.user.dto;

import java.math.BigDecimal;

/** 하루 변동이 임계값을 넘은 보유 종목 한 건. NOTI-02 배치가 읽는다. */
public record VolatilityHit(
        Long userId,
        String symbol,
        BigDecimal prevClose,
        BigDecimal todayClose,
        BigDecimal changeRate) {
}
