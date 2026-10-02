package com.example.mijang.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 배당락일 임박 판정 한 건이다 — payableDate는 벤더가 아직 안 줬으면 null이다. */
public record DividendExDateHit(
        Long userId,
        String symbol,
        LocalDate exDate,
        LocalDate payableDate,
        BigDecimal amountPerShare) {
}
