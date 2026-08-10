package com.example.mijang.common.type;

import java.math.RoundingMode;

/**
 * 수량 — {@code DECIMAL(18,6)}. 소수점 매수에 대응한다.
 *
 * <p>[[미장-DB명세서]] 4.1 · [[미장-기획서]] 8장
 * <p>매퍼에서 {@code typeHandler="quantity"} 로 쓴다.
 *
 * <p>내림(DOWN)이다. 반올림하면 보유하지 않은 주식이 생긴다. 6자리에서 잘라 버리는 쪽이
 * 매도 가능 수량을 넘기는 것보다 안전하다.
 */
public class QuantityTypeHandler extends ScaledDecimalTypeHandler {

    public static final int SCALE = 6;

    public QuantityTypeHandler() {
        super(SCALE, RoundingMode.DOWN);
    }
}
