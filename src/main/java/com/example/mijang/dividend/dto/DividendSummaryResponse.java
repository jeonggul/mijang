/*
 * DividendSummaryResponse — 배당 화면 상단 요약
 *
 * 이 파일이 하는 일
 *   요약 띠 세 칸이다 — 올해 누적(확정분만), 확정 대기, 다음 배당.
 *   1차(직접 입력)에서는 모두 확정이라 대기·다음 배당이 비어 있을 수 있고,
 *   화면은 그 자리를 — 로 그린다.
 */
package com.example.mijang.dividend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 배당 요약 응답. 화면 SR-016 요약 띠
 *
 * @param yearConfirmedKrw 올해 확정 배당 합(원). 세후
 * @param pendingCount     확정 대기 건수
 * @param pendingKrw       확정 대기 예상 금액 합(원)
 * @param nextPaySymbol    다음 배당 종목. 없으면 null
 * @param nextPayDate      다음 배당 지급일. 없으면 null
 */
public record DividendSummaryResponse(
        int year,
        BigDecimal yearConfirmedKrw,
        long pendingCount,
        BigDecimal pendingKrw,
        String nextPaySymbol,
        LocalDate nextPayDate) {
}
