package com.example.mijang.common.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** 휴장일·정규장 판정이다. 주말만 거르므로 미국 공휴일은 true 로 나온다(P3-3에서 보완 예정). */
@Component
public class MarketCalendar {

    /** 거래일 여부를 반환한다. */
    public boolean isTradingDay(LocalDate tradeDate) {
        DayOfWeek dow = tradeDate.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }
}
