package com.example.mijang.portfolio.domain;

import java.math.BigDecimal;

/** 매매 원장에서 계산된 한 종목의 보유 현황이다. 수량 0 이어도 실현손익 보존을 위해 남긴다. */
public record Holding(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal avgFxRate,
        BigDecimal totalFee,
        BigDecimal realizedPnlKrw) {

    /** 지금 보유 중인 종목인지 판별한다. */
    public boolean held() {
        return quantity.compareTo(BigDecimal.ZERO) > 0;
    }

    /** 매입 원가(USD)를 계산한다. */
    public BigDecimal costUsd() {
        return quantity.multiply(avgPrice);
    }
}
