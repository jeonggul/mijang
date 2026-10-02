/*
 * StockDetailResponse — 종목 상세
 *
 * 이 파일이 하는 일
 *   종목 상세 화면이 받아 가는 한 덩어리다.
 *
 *   등락률에 <b>세션이 함께 온다.</b> 기준가가 구간마다 다르기 때문이다(2.15).
 *   정규장에는 전일 종가 대비를, 장이 끝난 뒤에는 당일 정규장 종가 대비를 보여준다.
 *   화면은 실시간 체결이 올 때마다 {@code basePrice} 를 기준으로 다시 계산한다.
 *
 *   {@code session} 이 {@code CLOSED} 면 값이 멈춰 있어야 한다. 현재가는 마지막 거래일의
 *   정규장 종가이고, 화면은 실시간 스트림을 붙이지 않는다.
 */
package com.example.mijang.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

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
