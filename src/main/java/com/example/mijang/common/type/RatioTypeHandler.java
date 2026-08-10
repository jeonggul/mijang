package com.example.mijang.common.type;

import java.math.RoundingMode;

/**
 * 비율 — {@code DECIMAL(9,4)}. 0.0408 이 +4.08% 를 뜻한다.
 *
 * <p>[[미장-DB명세서]]
 * <p>매퍼에서 {@code typeHandler="ratio"} 로 쓴다.
 * <p>대상: {@code return_rate} · {@code withholding_rate} · {@code volatility_threshold}.
 *
 * <p>퍼센트가 아니라 소수로 저장한다. 화면에서 100 을 곱하는 것은 표시의 몫이고,
 * 저장값에 100 을 곱해 두면 계산할 때마다 되돌려야 한다.
 */
public class RatioTypeHandler extends ScaledDecimalTypeHandler {

    public static final int SCALE = 4;

    public RatioTypeHandler() {
        super(SCALE, RoundingMode.HALF_UP);
    }
}
