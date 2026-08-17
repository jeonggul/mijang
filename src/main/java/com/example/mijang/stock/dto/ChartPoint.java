/*
 * ChartPoint — 차트의 점 하나
 *
 * 이 파일이 하는 일
 *   봉 하나를 담는다. 시가·고가·저가·종가가 다 있어서 화면이 선으로도, 캔들로도 그릴 수 있다.
 *
 *   시각을 문자열로 담는 이유 — 일봉은 날짜("2026-08-13")이고 분봉은 시각까지
 *   필요하다("2026-08-13T13:31:00Z"). 타입을 나누면 화면이 두 갈래로 갈라진다.
 */
package com.example.mijang.stock.dto;

import java.math.BigDecimal;

public record ChartPoint(
        String at,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {
}
