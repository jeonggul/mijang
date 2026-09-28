package com.example.mijang.market.dto;

import java.time.Instant;

/** 헤더의 실시간 장 상태 표시에 쓰는 응답이다. */
public record MarketStatusResponse(String session, String label, boolean regular, Instant serverTime) {
}
