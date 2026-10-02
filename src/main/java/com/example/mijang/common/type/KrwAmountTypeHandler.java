package com.example.mijang.common.type;

import java.math.RoundingMode;

/** 원화 금액(DECIMAL(18,2)) 핸들러다. 원 단위로 잘라 저장하면 손익 합계 검증이 절사 오차로 어긋난다. */
public class KrwAmountTypeHandler extends ScaledDecimalTypeHandler {

    public static final int SCALE = 2;

    public KrwAmountTypeHandler() {
        super(SCALE, RoundingMode.HALF_UP);
    }
}
