package com.example.mijang.market.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/** 거래일 하루의 개장·마감 시각(미국 동부 기준)을 담는다. 표에 있는 날이 거래일이며 휴장일은 행 자체가 없다. */
public record MarketDay(
        LocalDate tradeDate,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime sessionOpen,
        LocalTime sessionClose) {

    /** 조기폐장일인지 반환한다. */
    public boolean earlyClose() {
        return closeTime.isBefore(LocalTime.of(16, 0));
    }
}
