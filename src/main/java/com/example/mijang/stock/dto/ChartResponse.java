/*
 * ChartResponse — 차트 한 장
 *
 * 이 파일이 하는 일
 *   점 목록에 더해 "이게 어떤 기간의, 어떤 시간대 값인가"를 함께 알려 준다.
 *
 *   화면이 이 정보를 쓴다 — 분봉이면 가로축에 시각을 쓰고 일봉이면 날짜를 쓴다.
 *   실시간이 얹히는 구간인지도 여기서 판단한다.
 */
package com.example.mijang.stock.dto;

import java.util.List;

public record ChartResponse(
        String symbol,
        String range,
        String timeframe,
        boolean intraday,
        List<ChartPoint> points) {
}
