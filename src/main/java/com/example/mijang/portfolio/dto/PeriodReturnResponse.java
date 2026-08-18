/*
 * PeriodReturnResponse — 기간 수익률
 *
 * 이 파일이 하는 일
 *   "3개월 동안 얼마나 벌었나"에 대한 답이다.
 *   기간의 첫 스냅샷과 마지막 스냅샷 두 건만 비교해 만든다.
 *   중간에 더 사고 판 것은 반영하지 않는다 — 단순 평가액 비교라 한계가 있다.
 */
package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 기간 수익률. 개발명세서(API) PROFIT-06
 *
 * <p>시작·끝 스냅샷 두 건만 읽어 만든다(2.1).
 *
 * <p>중간 추가 매수는 반영하지 않는다. 단순 평가액 비교라 한계가 있다(2.7).
 */
public record PeriodReturnResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal startValueKrw,
        BigDecimal endValueKrw,
        BigDecimal changeKrw,
        BigDecimal returnRate) {
}
