package com.example.mijang.common.type;

import java.math.RoundingMode;

/** 환율(DECIMAL(10,4)) 핸들러다. 4자리를 깎으면 환차손익 분해가 어긋나므로 scale 을 바꾸면 안 된다. */
public class FxRateTypeHandler extends ScaledDecimalTypeHandler {

    public static final int SCALE = 4;

    public FxRateTypeHandler() {
        super(SCALE, RoundingMode.HALF_UP);
    }
}
