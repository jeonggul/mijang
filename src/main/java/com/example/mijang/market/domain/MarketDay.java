/*
 * MarketDay — 거래일 하루
 *
 * 이 파일이 하는 일
 *   그날의 개장·마감 시각을 담는다. 전부 미국 동부 기준이다.
 *
 *   이 표에 <b>있는 날이 거래일</b>이다. 휴장일은 행 자체가 없다.
 *   쉬는 날을 모으는 것보다 여는 날을 모으는 편이 판단하기 쉽다 —
 *   "오늘이 거래일인가" 를 없는 것 확인이 아니라 있는 것 확인으로 답할 수 있다.
 *
 *   조기폐장일은 마감이 13:00 이고 애프터마켓도 17:00 에 끝난다. 값이 다를 뿐
 *   구조는 같아서 따로 표시할 필요가 없다.
 */
package com.example.mijang.market.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record MarketDay(
        LocalDate tradeDate,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime sessionOpen,
        LocalTime sessionClose) {

    /** 조기폐장일인가. 보통 16:00 에 닫는다 */
    public boolean earlyClose() {
        return closeTime.isBefore(LocalTime.of(16, 0));
    }
}
