/*
 * DividendResponse — 배당 한 건의 화면용 모양
 *
 * 이 파일이 하는 일
 *   배당 관리 화면의 표 한 줄이다. 주당 배당·배당락일 같은 벤더 값은
 *   직접 입력(1차)에는 없어서 null 로 나간다 — 화면은 — 로 그린다.
 */
package com.example.mijang.dividend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 배당 한 건 응답. 화면 SR-016
 */
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
