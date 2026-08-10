package com.example.mijang.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 거래일·시간 기준. 기획서 8.1 을 그대로 따른다.
 *
 * <p>모든 타임스탬프는 UTC 로 저장하고, 거래일은 미국 동부(ET) 날짜를 별도 컬럼으로 둔다.
 * 서머타임은 직접 계산하지 않고 IANA 타임존에 위임한다.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.time
 */
@Component
public class TradingClock {

    public static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** UTC 시각 → 미국 현지 거래일(trade_date). 집계·조회는 이 값을 기준으로 한다. */
    public LocalDate tradeDate(Instant at) {
        return at.atZone(MARKET_ZONE).toLocalDate();
    }

    public LocalDate today() {
        return tradeDate(Instant.now());
    }
}
