/*
 * FxRate — 그날의 확정 환율
 *
 * 이 파일이 하는 일
 *   하루에 하나씩 정해지는 원달러 환율을 담는다. fx_rates 한 행에 대응한다.
 *
 *   환차손익과 일별 스냅샷이 이 값을 집어간다. 그래서 하루가 끝나면 다시 바뀌면 안 된다.
 *   시시각각 움직이는 값은 FxQuote 가 따로 담는다.
 *
 *   비영업일에는 그날 값이 없다. 미국 장은 한국 주말에도 열리므로(금요일 밤)
 *   반드시 생기는 일이다. 직전 값을 복사해 두고 복사했다는 사실을 함께 남긴다 —
 *   화면이 "직전 영업일 기준" 배지를 띄우는 근거가 이 플래그다.
 */
package com.example.mijang.fx.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 그날의 확정 환율. {@code fx_rates} 한 행.
 *
 * @param rateDate        기준일
 * @param usdKrw          1 USD 당 원화
 * @param source          출처. {@code OPENEXCHANGERATES}
 * @param substituted     직전 값을 복사한 행이면 true
 * @param substitutedFrom 복사해 온 날짜. 복사가 아니면 null
 */
public record FxRate(LocalDate rateDate,
                     BigDecimal usdKrw,
                     String source,
                     boolean substituted,
                     LocalDate substitutedFrom,
                     Instant collectedAt) {

    /** 벤더에서 그날 값을 실제로 받은 경우. */
    public static FxRate confirmed(LocalDate date, BigDecimal usdKrw) {
        return confirmed(date, usdKrw, Instant.now());
    }

    /** 받아 넣은 시각을 직접 정한다. 시험이 시계를 고정할 때 쓴다. */
    public static FxRate confirmed(LocalDate date, BigDecimal usdKrw, Instant at) {
        return new FxRate(date, usdKrw, "OPENEXCHANGERATES", false, null, at);
    }

    /**
     * 그날 값이 없어 직전 값을 복사한 경우.
     *
     * <p>복사해 온 값이므로 받아 넣은 시각도 원본의 것을 그대로 들고 간다 —
     * 복사한 순간을 적으면 오늘 새로 받아 온 값처럼 보인다.
     */
    public static FxRate substitute(LocalDate date, BigDecimal usdKrw, LocalDate from,
                                    Instant originalCollectedAt) {
        return new FxRate(date, usdKrw, "OPENEXCHANGERATES", true, from, originalCollectedAt);
    }
}
