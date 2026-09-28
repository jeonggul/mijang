package com.example.mijang.fx.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 그날의 확정 원달러 환율({@code fx_rates} 한 행)을 담는다. */
public record FxRate(LocalDate rateDate,
                     BigDecimal usdKrw,
                     String source,
                     boolean substituted,
                     LocalDate substitutedFrom,
                     Instant collectedAt) {

    /** 벤더에서 그날 값을 실제로 받은 확정 환율을 만든다. */
    public static FxRate confirmed(LocalDate date, BigDecimal usdKrw) {
        return confirmed(date, usdKrw, Instant.now());
    }

    /** 수집 시각을 직접 지정해 확정 환율을 만든다(테스트용). */
    public static FxRate confirmed(LocalDate date, BigDecimal usdKrw, Instant at) {
        return new FxRate(date, usdKrw, "OPENEXCHANGERATES", false, null, at);
    }

    /** 직전 값을 복사한 대체 환율을 만든다. 수집 시각도 원본 것을 그대로 쓴다. */
    public static FxRate substitute(LocalDate date, BigDecimal usdKrw, LocalDate from,
                                    Instant originalCollectedAt) {
        return new FxRate(date, usdKrw, "OPENEXCHANGERATES", true, from, originalCollectedAt);
    }
}
