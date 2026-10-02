package com.example.mijang.common.type;

import java.math.RoundingMode;

/** 달러 금액(DECIMAL(18,4)) 핸들러다. 벤더 시세가 4자리로 오므로 2자리로 깎으면 체결가가 바뀐다. */
public class UsdAmountTypeHandler extends ScaledDecimalTypeHandler {

    public static final int SCALE = 4;

    public UsdAmountTypeHandler() {
        super(SCALE, RoundingMode.HALF_UP);
    }
}
