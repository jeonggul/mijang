package com.example.mijang.admin.dto;

/** 관심 등록과 현재 보유 사용자 수로 보는 인기 종목. */
public record AdminPopularStockResponse(
        String symbol,
        String name,
        String nameKo,
        int watcherCount,
        int holderCount) {
}
