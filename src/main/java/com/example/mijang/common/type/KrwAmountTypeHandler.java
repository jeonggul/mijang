package com.example.mijang.common.type;

import java.math.RoundingMode;

/**
 * 원화 금액 — {@code DECIMAL(18,2)}.
 *
 * <p>[[미장-DB명세서]] 4.1 — "원 단위 표시, 계산 중간값 소수 허용"
 * <p>매퍼에서 {@code typeHandler="krwAmount"} 로 쓴다.
 * <p>대상: {@code market_value_krw} · {@code cost_basis_krw} · {@code price_pnl_krw}
 * · {@code fx_pnl_krw} · {@code total_pnl_krw} · {@code realized_pnl_krw}.
 *
 * <p>화면에는 원 단위 정수로 보여 주지만 저장은 2자리다. 원 단위로 잘라 저장하면
 * 주가손익 + 환차손익 = 합계 검증이 절사 오차로 어긋난다. 표시 반올림은 화면의 몫이다.
 */
public class KrwAmountTypeHandler extends ScaledDecimalTypeHandler {

    public static final int SCALE = 2;

    public KrwAmountTypeHandler() {
        super(SCALE, RoundingMode.HALF_UP);
    }
}
