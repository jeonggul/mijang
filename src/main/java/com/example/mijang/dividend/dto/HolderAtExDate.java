package com.example.mijang.dividend.dto;

import java.math.BigDecimal;

/** 배당락일 시점의 보유자와 보유 수량을 담는다. PROFIT-12. */
public record HolderAtExDate(Long userId, Long portfolioId, BigDecimal quantity) {
}
