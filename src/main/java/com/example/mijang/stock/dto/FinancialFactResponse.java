package com.example.mijang.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** XBRL 재무 항목 한 기간치의 응답이다. quarterly 는 분기 실적이면 true, 누적이면 false 다. */
public record FinancialFactResponse(
        String tag,
        String unit,
        LocalDate start,
        LocalDate end,
        BigDecimal value,
        boolean quarterly,
        String form,
        Integer fiscalYear,
        String fiscalPeriod,
        LocalDate filed) {
}
