/*
 * DividendExDateHit — 배당락일이 다가온 보유 종목 하나
 *
 * 이 파일이 하는 일
 *   배당 알림(NOTI-04) 첫 갈래의 판정 결과다. 배당락일 전일까지 보유해야
 *   배당이 나오므로, 락일이 오기 전에 알려야 의미가 있다.
 */
package com.example.mijang.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 배당락일 임박 판정 한 건. NOTI-04
 *
 * @param payableDate 지급일. 벤더가 아직 안 줬으면 null
 */
public record DividendExDateHit(
        Long userId,
        String symbol,
        LocalDate exDate,
        LocalDate payableDate,
        BigDecimal amountPerShare) {
}
