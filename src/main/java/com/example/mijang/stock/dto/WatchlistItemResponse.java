package com.example.mijang.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 관심종목 한 건. 개발명세서(API) WATCH-02
 *
 * <p>시세를 함께 담는다. 목록만 주고 화면이 종목마다 상세를 다시 부르면
 * 20종목에 21번 요청이 된다.
 */
public record WatchlistItemResponse(
        Long id,
        String symbol,
        String name,
        String nameKo,
        boolean active,
        BigDecimal currentPrice,
        BigDecimal dayChangeRate,
        LocalDate asOf) {
}
