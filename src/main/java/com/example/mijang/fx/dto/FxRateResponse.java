/*
 * FxRateResponse — 화면에 나가는 환율
 *
 * 이 파일이 하는 일
 *   환율 하나를 화면이 쓸 모양으로 담는다.
 *
 *   숫자만 주지 않고 "언제 값인지"와 "대체된 값인지"를 함께 준다.
 *   그것이 없으면 화면은 금요일 값을 토요일 환율로 그려 놓게 된다.
 *   환율이 없는 것은 오류가 아니라서(미장-API명세서 1.6) 응답은 언제나 200 이고,
 *   구분은 이 필드들이 한다.
 */
package com.example.mijang.fx.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 환율 응답. {@code GLOBAL-01}
 *
 * @param rate            1 USD 당 원화
 * @param rateDate        기준일
 * @param substituted     직전 값을 복사한 것이면 true — 화면이 "직전 영업일 기준" 배지를 띄운다
 * @param substitutedFrom 복사해 온 날짜. 복사가 아니면 null
 * @param quotedAt        벤더가 찍은 시각. 현재 환율 조회에서만 채워진다
 * @param lastUpdatedAt   DB에 마지막으로 수집된 시세 시각. 확정값으로 대체해도 갱신 상태를 알 수 있게 한다
 */
public record FxRateResponse(BigDecimal rate,
                             LocalDate rateDate,
                             boolean substituted,
                             LocalDate substitutedFrom,
                             Instant quotedAt,
                             Instant lastUpdatedAt) {

    /** 기존 호출부는 현재 시세라면 그 시각을 마지막 갱신 시각으로도 쓴다. */
    public FxRateResponse(BigDecimal rate,
                          LocalDate rateDate,
                          boolean substituted,
                          LocalDate substitutedFrom,
                          Instant quotedAt) {
        this(rate, rateDate, substituted, substitutedFrom, quotedAt, quotedAt);
    }

    /** 확정 환율(일별)에서 만든다. 시세 시각은 의미가 없어 비운다. */
    public static FxRateResponse ofDaily(com.example.mijang.fx.domain.FxRate r) {
        return new FxRateResponse(r.usdKrw(), r.rateDate(), r.substituted(), r.substitutedFrom(), null);
    }

    /** 현재 시세에서 만든다. 그날 확정이 아니므로 대체 여부는 따지지 않는다. */
    public static FxRateResponse ofLive(com.example.mijang.fx.domain.FxQuote q, LocalDate date) {
        return new FxRateResponse(q.basePrice(), date, false, null, q.quotedAt());
    }

    /** 반환값은 확정 환율이지만 마지막 수집 상태는 별도 메타데이터로 보존한다. */
    public FxRateResponse withLastUpdatedAt(Instant value) {
        return new FxRateResponse(rate, rateDate, substituted, substitutedFrom, quotedAt, value);
    }
}
