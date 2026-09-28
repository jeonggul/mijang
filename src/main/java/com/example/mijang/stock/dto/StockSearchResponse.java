package com.example.mijang.stock.dto;

/** 종목 검색 결과 한 건의 응답이다. nameKo 가 없으면 null 이고 화면은 영문명을 쓴다. */
public record StockSearchResponse(
        String symbol,
        String name,
        String nameKo,
        String exchange,
        String assetClass,
        boolean active,
        /* 직전 종가 대비 등락률(%). 종가가 없거나 0 이면 null 이다 */
        java.math.BigDecimal dayChangeRate) {
}
