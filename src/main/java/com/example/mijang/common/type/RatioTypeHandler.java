package com.example.mijang.common.type;

import java.math.RoundingMode;

/** 비율(DECIMAL(9,4)) 핸들러다. 퍼센트가 아니라 소수로 저장한다(0.0408 = +4.08%). */
public class RatioTypeHandler extends ScaledDecimalTypeHandler {

    public static final int SCALE = 4;

    public RatioTypeHandler() {
        super(SCALE, RoundingMode.HALF_UP);
    }
}
