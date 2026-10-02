package com.example.mijang.stock.dto;

/**
 * 종목 검색 결과 한 건. 개발명세서(API) SEARCH-01
 *
 * <p>{@code nameKo} 는 한글 종목명이다. 없으면 null 이고 화면은 영문명을 쓴다.
 * 개별주는 대부분 있으나 ETF 는 거의 없다(2.22).
 *
 * <p>{@code active} 를 함께 내보낸다. 상장폐지 종목도 검색에는 걸리는데,
 * 화면이 그 사실을 표시하지 못하면 사용자가 매매 기록을 넣으려다 막힌다.
 */
public record StockSearchResponse(
        String symbol,
        String name,
        String nameKo,
        String exchange,
        String assetClass,
        boolean active,
        /* 직전 종가 대비 등락률(%). 종가가 없거나 0 이면 null 이다 —
           0% 로 채우면 "안 움직였다" 로 읽힌다 */
        java.math.BigDecimal dayChangeRate) {
}
