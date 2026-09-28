package com.example.mijang.stock.dto;

import java.util.List;

/** 차트 한 장의 응답 — 점 목록과 기간·시간대 정보를 함께 담는다. */
public record ChartResponse(
        String symbol,
        String range,
        String timeframe,
        boolean intraday,
        List<ChartPoint> points) {
}
