/*
 * QuoteResponse — 현재가 한 건
 *
 * 이 파일이 하는 일
 *   화면으로 나가는 시세의 모양이다.
 *   실시간 값인지 마지막 종가인지를 함께 알려 준다. 장이 닫혀 있으면
 *   종가를 주는데, 그것을 실시간인 척 보내면 사용자가 지금 값으로 오해한다.
 *   이 값으로 손익을 계산하지는 않는다 — 손익은 일봉 종가를 쓴다.
 */
package com.example.mijang.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 현재가 한 건. 개발명세서(API) PRICE-01
 *
 * <p>{@code live} 가 false 면 실시간이 아니라 <b>마지막 종가</b>다(2.5·2.8).
 *
 * <p>{@code delayed} 는 값이 움직이긴 하되 15분 늦었다는 뜻이다(2.11).
 * 장 시간 밖에는 IEX 에 체결이 거의 없어 이쪽으로만 값이 온다.
 * 실시간인 척 보여 주면 사용자가 지금 값으로 오해한다.
 * 화면은 이 값으로 "장 마감" 배지를 붙인다.
 *
 * <p>이 값으로 손익을 계산하지 않는다(2.4).
 */
public record QuoteResponse(
        String symbol,
        BigDecimal price,
        Instant at,
        boolean live,
        boolean delayed) {
}
