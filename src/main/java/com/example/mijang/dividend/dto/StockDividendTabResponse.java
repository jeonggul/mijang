package com.example.mijang.dividend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 종목 배당 탭(INFO-06)의 이력·요약 응답을 담는다. */
public record StockDividendTabResponse(
        List<Item> history,
        BigDecimal yieldPct,
        BigDecimal annualAmountUsd,
        int perYear,
        int streakYears) {

    /** 배당 이력 한 줄을 나타낸다. upcoming 이면 지급일이 아직 오지 않았다. */
    public record Item(
            LocalDate exDate,
            LocalDate payDate,
            BigDecimal amountPerShare,
            boolean special,
            boolean upcoming) {
    }
}
