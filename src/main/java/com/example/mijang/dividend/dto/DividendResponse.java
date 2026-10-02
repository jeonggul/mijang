package com.example.mijang.dividend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 배당 한 건의 화면(SR-016) 응답을 담는다. 벤더 전용 값은 직접 입력 건에서 null 이다. */
public record DividendResponse(
        Long id,
        String symbol,
        LocalDate exDate,
        LocalDate payDate,
        BigDecimal amountPerShare,
        BigDecimal quantityAtExDate,
        BigDecimal netAmountUsd,
        BigDecimal fxRate,
        BigDecimal netAmountKrw,
        String status,
        String source) {
}
