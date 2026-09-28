package com.example.mijang.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** 거래일·시간 기준이다. 타임스탬프는 UTC 저장, 거래일은 미국 동부(ET) 날짜 — 이 기준을 바꾸면 집계가 깨진다. */
@Component
public class TradingClock {

    public static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** UTC 시각을 미국 현지 거래일(trade_date)로 바꾼다. */
    public LocalDate tradeDate(Instant at) {
        return at.atZone(MARKET_ZONE).toLocalDate();
    }

    /** 현재 시각 기준 거래일을 반환한다. */
    public LocalDate today() {
        return tradeDate(Instant.now());
    }
}
