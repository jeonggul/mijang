package com.example.mijang.stock.dto;

/** 종목 검색 결과 한 건. 개발명세서(API) STOCK-001 */
public record StockSearchResponse(String symbol, String name, String exchange) {
}
