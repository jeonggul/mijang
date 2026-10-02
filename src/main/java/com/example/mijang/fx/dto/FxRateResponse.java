package com.example.mijang.fx.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 환율 응답 DTO 다. 값과 함께 기준일·대체 여부·수집 시각을 담는다. {@code GLOBAL-01} */
public record FxRateResponse(BigDecimal rate,
                             LocalDate rateDate,
                             boolean substituted,
                             LocalDate substitutedFrom,
                             Instant quotedAt,
                             Instant lastUpdatedAt) {

    /** 시세 시각을 마지막 갱신 시각으로도 쓰는 축약 생성자다. */
    public FxRateResponse(BigDecimal rate,
                          LocalDate rateDate,
                          boolean substituted,
                          LocalDate substitutedFrom,
                          Instant quotedAt) {
        this(rate, rateDate, substituted, substitutedFrom, quotedAt, quotedAt);
    }

    /** 일별 확정 환율에서 응답을 만든다. {@code quotedAt} 은 비우고 수집 시각을 싣는다. */
    public static FxRateResponse ofDaily(com.example.mijang.fx.domain.FxRate r) {
        return new FxRateResponse(r.usdKrw(), r.rateDate(), r.substituted(), r.substitutedFrom(),
                                  null, r.collectedAt());
    }

    /** 현재 시세에서 응답을 만든다. */
    public static FxRateResponse ofLive(com.example.mijang.fx.domain.FxQuote q, LocalDate date) {
        return new FxRateResponse(q.basePrice(), date, false, null, q.quotedAt());
    }

    /** 마지막 수집 시각만 바꾼 사본을 만든다. */
    public FxRateResponse withLastUpdatedAt(Instant value) {
        return new FxRateResponse(rate, rateDate, substituted, substitutedFrom, quotedAt, value);
    }
}
