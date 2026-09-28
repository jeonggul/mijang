package com.example.mijang.dividend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 배당 화면(SR-016) 요약 띠 — 올해 확정 누적·확정 대기·다음 배당 — 를 담는다. */
public record DividendSummaryResponse(
        int year,
        BigDecimal yearConfirmedKrw,
        long pendingCount,
        BigDecimal pendingKrw,
        String nextPaySymbol,
        LocalDate nextPayDate) {
}
