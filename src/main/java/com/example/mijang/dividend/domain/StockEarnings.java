package com.example.mijang.dividend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** stock_earnings 한 행을 나타낸다. */
public record StockEarnings(String symbol, LocalDate reportDate, LocalDate fiscalDateEnding,
                            BigDecimal estimateEps, String timeOfDay) {
}
