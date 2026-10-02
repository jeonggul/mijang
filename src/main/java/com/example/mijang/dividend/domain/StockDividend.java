/*
 * StockDividend — 종목 배당 이벤트 한 건
 *
 * 이 파일이 하는 일
 *   stock_dividends 테이블의 한 행이다. 보유 여부와 무관한 종목 자체의
 *   배당 이력·일정이고, 사용자별 수령 내역(dividends)과는 다른 표다.
 *   예상 배당 생성(PROFIT-12)과 종목 배당 탭(INFO-06)이 여기서 읽는다.
 */
package com.example.mijang.dividend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * stock_dividends 한 행. 개발명세서(API) PROFIT-12 · INFO-06
 *
 * <p>{@code special} 은 연간 배당 추정에서 빼야 하고(스키마 주석),
 * {@code foreign} 은 원천징수율이 15% 가 아닐 수 있다는 표시다.
 */
public record StockDividend(
        String symbol,
        LocalDate exDate,
        String dividendType,
        BigDecimal amountPerShare,
        LocalDate recordDate,
        LocalDate payableDate,
        LocalDate processDate,
        boolean special,
        boolean foreign,
        String cusip,
        String vendorId) {
}
