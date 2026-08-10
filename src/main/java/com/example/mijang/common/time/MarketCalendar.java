package com.example.mijang.common.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 휴장일·정규장 판정.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.time
 * <p>TODO: 주말만 걸러내는 상태다. Alpaca 휴장일 캘린더를 받아 공휴일·조기 폐장을 반영해야 한다.
 */
@Component
public class MarketCalendar {

    public boolean isTradingDay(LocalDate tradeDate) {
        DayOfWeek dow = tradeDate.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }
}
