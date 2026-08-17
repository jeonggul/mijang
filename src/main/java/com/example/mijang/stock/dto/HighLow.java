package com.example.mijang.stock.dto;

import java.math.BigDecimal;

/**
 * 기간 최고·최저. 개발명세서(API) PRICE-04
 *
 * <p>일봉이 하나도 없으면 두 값 모두 null 이다. 화면은 그때 "—" 를 표시한다.
 */
public record HighLow(BigDecimal high, BigDecimal low) {
}
