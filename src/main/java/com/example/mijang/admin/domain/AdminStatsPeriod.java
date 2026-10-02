package com.example.mijang.admin.domain;

import com.example.mijang.common.time.TradingClock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** 관리자 통계의 KST 달력 기간. */
public enum AdminStatsPeriod {
    DAY("1D"),
    WEEK("1W"),
    MONTH("1M");

    private final String code;

    AdminStatsPeriod(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AdminStatsPeriod from(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        for (AdminStatsPeriod period : values()) {
            if (period.code.equals(code)) {
                return period;
            }
        }
        return MONTH;
    }

    public Window window(LocalDate today) {
        LocalDate from = switch (this) {
            case DAY -> today;
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
        };
        LocalDate to = today.plusDays(1);
        LocalDate previousFrom = switch (this) {
            case DAY -> from.minusDays(1);
            case WEEK -> from.minusWeeks(1);
            case MONTH -> from.minusMonths(1);
        };
        long elapsedDays = ChronoUnit.DAYS.between(from, to);
        LocalDate candidateEnd = previousFrom.plusDays(elapsedDays);
        LocalDate previousPeriodEnd = candidateEnd.isAfter(from) ? from : candidateEnd;
        return new Window(code, from, today, utc(previousFrom), utc(previousPeriodEnd),
                utc(from), utc(to));
    }

    private static LocalDateTime utc(LocalDate date) {
        return date.atStartOfDay(TradingClock.SERVICE_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /** 현재 기간과 같은 경과 일수만 이전 기간에서 비교한다. */
    public record Window(
            String code,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDateTime previousFromUtc,
            LocalDateTime previousToUtc,
            LocalDateTime fromUtc,
            LocalDateTime toUtc) {
    }
}
