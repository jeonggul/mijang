package com.example.mijang.common.type;

import java.math.RoundingMode;

/** 수량(DECIMAL(18,6)) 핸들러다. 반올림하면 보유하지 않은 주식이 생기므로 내림(DOWN)을 바꾸면 안 된다. */
public class QuantityTypeHandler extends ScaledDecimalTypeHandler {

    public static final int SCALE = 6;

    public QuantityTypeHandler() {
        super(SCALE, RoundingMode.DOWN);
    }
}
