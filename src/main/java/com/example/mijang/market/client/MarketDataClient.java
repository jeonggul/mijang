package com.example.mijang.market.client;

import java.util.Collection;

/** 시세 벤더를 추상화한다. */
public interface MarketDataClient {

    /** 실시간 구독 대상을 갱신한다. */
    void subscribe(Collection<String> symbols);

    /** 실시간 구독을 해제한다. */
    void unsubscribe(Collection<String> symbols);
}
