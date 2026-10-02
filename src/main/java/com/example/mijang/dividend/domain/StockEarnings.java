package com.example.mijang.dividend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** stock_earnings 한 행. 화면·API 가 쓰는 값만. */
public record StockEarnings(String symbol, LocalDate reportDate, LocalDate fiscalDateEnding,
                            BigDecimal estimateEps, String timeOfDay) {
}
