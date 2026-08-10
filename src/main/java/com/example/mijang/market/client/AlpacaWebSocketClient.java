package com.example.mijang.market.client;

import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * Alpaca 실시간 시세 WebSocket 클라이언트(IEX 피드).
 *
 * <p>개발명세서(MVC) 원문은 FinnhubWebSocketClient 이지만, 시세 벤더가 Alpaca 로 확정되어
 * 이름을 바꿨다. (외부-데이터-출처 2장 · Finnhub 무료 티어는 일봉이 403)
 * <p>IEX 는 전체 거래량의 약 2% 라 실시간은 표시용이고, 손익 계산은 SIP 일봉 종가를 쓴다.
 * <p>TODO: 아직 연결하지 않는다. 의존성·인증키 준비 후 구현한다.
 */
@Component
public class AlpacaWebSocketClient implements MarketDataClient {

    @Override
    public void subscribe(Collection<String> symbols) {
        throw new UnsupportedOperationException("TODO: Alpaca WS 구독");
    }

    @Override
    public void unsubscribe(Collection<String> symbols) {
        throw new UnsupportedOperationException("TODO: Alpaca WS 구독 해제");
    }
}
