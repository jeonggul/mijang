/*
 * StockDividendTabResponse — 종목 배당 탭의 응답
 *
 * 이 파일이 하는 일
 *   종목 화면 배당 탭(INFO-06)이 그리는 전부 — 이력 몇 건과 요약 네 칸.
 *   내 수령액은 여기에 없다. 이 API 는 비로그인도 보는 종목 정보라,
 *   내 것은 화면이 /api/dividends 를 따로 읽어 붙인다.
 */
package com.example.mijang.dividend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 종목 배당 탭 응답. 개발명세서(API) INFO-06 · 화면 stock p4
 *
 * @param yieldPct        배당수익률(%). 최근 1년 배당 합 ÷ 최신 종가. 종가가 없으면 null
 * @param annualAmountUsd 최근 1년 주당 배당 합(USD). 특별배당 제외
 * @param perYear         최근 1년 배당 횟수. 주기 표시(분기·월…)에 쓴다
 * @param streakYears     연속 증배 연수. 완결된 해의 연간 합끼리 비교한다
 */
public record StockDividendTabResponse(
        List<Item> history,
        BigDecimal yieldPct,
        BigDecimal annualAmountUsd,
        int perYear,
        int streakYears) {

    /** 이력 한 줄. {@code upcoming} 이면 지급일이 아직 오지 않았다 — 예정 배지. */
    public record Item(
            LocalDate exDate,
            LocalDate payDate,
            BigDecimal amountPerShare,
            boolean special,
            boolean upcoming) {
    }
}
