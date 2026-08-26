/*
 * DividendPayHit — 확정을 기다리는 예상 배당 하나
 *
 * 이 파일이 하는 일
 *   배당 알림(NOTI-04) 둘째 갈래의 판정 결과다. 예상 배당(PROFIT-12)이
 *   생긴 사용자에게 지급일과 예상액을 알리고 확정을 청한다.
 */
package com.example.mijang.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 예상 배당 지급 예정 판정 한 건. NOTI-04
 */
public record DividendPayHit(
        Long userId,
        String symbol,
        LocalDate payDate,
        BigDecimal netAmountUsd) {
}
