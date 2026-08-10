package com.example.mijang.common.type;

import java.math.RoundingMode;

/**
 * 달러 단가·금액 — {@code DECIMAL(18,4)}. 센트 아래 4자리까지.
 *
 * <p>[[미장-DB명세서]] 4.1
 * <p>매퍼에서 {@code typeHandler="usdAmount"} 로 쓴다.
 * <p>대상: {@code price} · {@code fee} · {@code avg_price} · {@code target_price}
 * · {@code market_value_usd} · 일봉 OHLC.
 *
 * <p>센트(2자리)가 아니라 4자리인 이유는 벤더 시세가 4자리로 오기 때문이다.
 * 2자리로 깎으면 체결가가 바뀐다.
 */
public class UsdAmountTypeHandler extends ScaledDecimalTypeHandler {

    public static final int SCALE = 4;

    public UsdAmountTypeHandler() {
        super(SCALE, RoundingMode.HALF_UP);
    }
}
