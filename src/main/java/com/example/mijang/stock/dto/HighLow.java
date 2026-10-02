package com.example.mijang.stock.dto;

import java.math.BigDecimal;

/** 기간 최고·최저가다. 일봉이 하나도 없으면 두 값 모두 null 이다. */
public record HighLow(BigDecimal high, BigDecimal low) {
}
