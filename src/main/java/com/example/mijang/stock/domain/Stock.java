package com.example.mijang.stock.domain;

import java.time.LocalDateTime;

/** stocks 테이블 한 행이다. */
public record Stock(
        Long id,
        String symbol,
        String name,
        String nameKo,
        String exchange,
        String assetClass,
        String sector,
        String cik,
        boolean fractionable,
        boolean isActive,
        String inactiveReason,
        LocalDateTime syncedAt) {

    /** 거래 가능한 종목인지. 상장폐지·티커 변경 종목은 false 다. */
    public boolean tradable() {
        return isActive;
    }

    /** ETF 인지. 화면에서 배지를 붙일 때 쓴다. */
    public boolean etf() {
        return "ETF".equals(assetClass);
    }
}
