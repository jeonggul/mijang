package com.example.mijang.stock.client;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Alpha Vantage EARNINGS_CALENDAR 한 줄. name·currency 는 안 쓰므로 담지 않는다. */
public record EarningsRow(String symbol, LocalDate reportDate,
                          LocalDate fiscalDateEnding, BigDecimal estimateEps, String timeOfDay) {
}
