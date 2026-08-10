package com.example.mijang.common.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 휴장일·정규장 판정.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.time
 * <p>지금은 주말만 걸러낸다. 공휴일·조기 폐장은 Alpaca 휴장일 캘린더를 붙여야 하는데,
 * 이것이 필요해지는 시점은 실시간 시세(MARKET-001~003)라서 [[미장-구현-우선순위]] P3-3 으로 미뤄 뒀다.
 * 그때까지는 미국 공휴일에 대해 {@code true} 를 돌려준다는 것을 아는 채로 쓴다.
 */
@Component
public class MarketCalendar {

    public boolean isTradingDay(LocalDate tradeDate) {
        DayOfWeek dow = tradeDate.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }
}
