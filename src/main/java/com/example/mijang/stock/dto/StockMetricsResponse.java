/*
 * StockMetricsResponse — 투자 지표
 *
 * 이 파일이 하는 일
 *   종목 상세 사이드바의 "투자 지표" 칸에 들어갈 값을 담는다.
 *   시가총액·PER·PBR·EPS·배당수익률과 기업 정보(산업·상장일)다.
 *
 *   시세가 아니라 <b>종목에 대한 정보</b>다. 출처가 Alpaca 가 아니라 Finnhub 인 이유가 그것이다.
 *
 *   전부 null 일 수 있다. ETF 는 PER 이 없고, 무배당 종목은 배당수익률이 없고,
 *   벤더가 아직 안 실은 종목은 통째로 비어 있다. 화면은 없는 값을 — 로 둔다.
 */
package com.example.mijang.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StockMetricsResponse(
        String symbol,
        BigDecimal marketCap,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal eps,
        BigDecimal dividendYield,
        BigDecimal beta,
        BigDecimal week52High,
        BigDecimal week52Low,
        String industry,
        String country,
        String webUrl,
        LocalDate ipoDate,
        String logoUrl,
        BigDecimal sharesOutstanding,
        LocalDateTime syncedAt) {
}
