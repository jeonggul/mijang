/*
 * Dividend — 배당 기록 한 건
 *
 * 이 파일이 하는 일
 *   dividends 테이블의 한 행이다. 1차(PROFIT-11)는 사용자가 직접 입력한
 *   확정 배당만 담고, 2차(PROFIT-12)에서 벤더가 만든 예상(ESTIMATED) 행이
 *   더해진다. 예상 행은 확정 전까지 손익 집계에서 빠진다.
 */
package com.example.mijang.dividend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * dividends 한 행. 개발명세서(API) PROFIT-11·12 · 화면 SR-016
 *
 * <p>{@code status} 가 ESTIMATED 면 손익 집계에서 제외된다 — 확정 전 금액은
 * 추정값이라 섞으면 확정 전후로 집계가 달라지는 이유를 화면이 설명할 수 없다.
 */
public record Dividend(
        Long id,
        Long userId,
        Long portfolioId,
        String symbol,
        LocalDate exDate,
        LocalDate payDate,
        BigDecimal amountPerShare,
        BigDecimal quantityAtExDate,
        BigDecimal grossAmountUsd,
        BigDecimal netAmountUsd,
        BigDecimal withholdingRate,
        BigDecimal fxRate,
        BigDecimal netAmountKrw,
        String status,
        String source,
        LocalDateTime confirmedAt) {

    /** 일반 주식 기준 원천징수율. REIT·MLP 는 다를 수 있다 — 화면이 고지한다. */
    public static final BigDecimal DEFAULT_WITHHOLDING = new BigDecimal("0.15");

    /** 확정됐는지. 확정 전에는 손익 집계에서 빠진다. */
    public boolean confirmed() {
        return "CONFIRMED".equals(status);
    }
}
