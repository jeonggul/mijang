package com.example.mijang.common.type;

import java.math.RoundingMode;

/**
 * 환율 — {@code DECIMAL(10,4)}. 원/달러. 1,408.4000 형태.
 *
 * <p>[[미장-DB명세서]] 4.1
 * <p>매퍼에서 {@code typeHandler="fxRate"} 로 쓴다.
 * <p>대상: {@code usd_krw} · {@code fx_rate} · {@code avg_fx_rate} · {@code applied_fx_rate}.
 *
 * <p>환차손익이 이 값에서 나온다. 4자리를 깎으면 손익 분해가 통째로 어긋난다.
 */
public class FxRateTypeHandler extends ScaledDecimalTypeHandler {

    public static final int SCALE = 4;

    public FxRateTypeHandler() {
        super(SCALE, RoundingMode.HALF_UP);
    }
}
