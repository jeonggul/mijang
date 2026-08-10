package com.example.mijang.market.client;

import java.util.Collection;

/**
 * 시세 벤더 추상화. 기획서 7장 — 벤더 교체 가능성에 대비해 인터페이스로 둔다.
 *
 * <p>개발명세서(MVC) · 실시간 시세 · client
 */
public interface MarketDataClient {

    /** 실시간 구독 대상을 갱신한다. */
    void subscribe(Collection<String> symbols);

    void unsubscribe(Collection<String> symbols);
}
