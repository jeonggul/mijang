package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 기간 시작·끝 스냅샷을 비교한 기간 수익률 응답이다. 중간 추가 매수는 반영하지 않는다. */
public record PeriodReturnResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal startValueKrw,
        BigDecimal endValueKrw,
        BigDecimal changeKrw,
        BigDecimal returnRate) {
}
