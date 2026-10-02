package com.example.mijang.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 종목 상세 화면이 받아 가는 응답이다. 등락률 기준가가 세션마다 달라 세션 정보가 함께 온다. */
public record StockDetailResponse(
        String symbol,
        String name,
        String nameKo,
        String exchange,
        String assetClass,
        boolean active,
        String inactiveReason,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal dayChangeRate,
        BigDecimal week52High,
        BigDecimal week52Low,
        LocalDate asOf,
        BigDecimal priceKrw,
        /** PRE·REGULAR·AFTER·CLOSED */
        String session,
        /** 화면에 그대로 쓰는 이름. 프리마켓·정규장·시간외·장 마감 */
        String sessionLabel,
        /** 이 세션의 등락률 기준가. 화면이 실시간 체결로 다시 계산할 때 쓴다 */
        BigDecimal basePrice,
        /** 마지막 거래일의 정규장 종가. 시간외 등락률의 기준이다 */
        BigDecimal regularClose,
        /** 마지막 거래일. 휴장일에는 이 날짜에서 화면이 멈춘다 */
        LocalDate lastTradingDay) {
}
