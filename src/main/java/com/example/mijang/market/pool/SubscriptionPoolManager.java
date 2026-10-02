/*
 * SubscriptionPoolManager — 구독 종목 한도를 지키는 곳
 *
 * 이 파일이 하는 일
 *   지금 벤더에 구독 중인 종목 목록을 들고 있다. 30종목 한도를 지키는
 *   유일한 장치다.
 *   같은 종목을 여러 사람이 봐도 구독은 하나로 친다. 한도를 넘으면
 *   새 요청을 받지 않는다 — 오래된 것을 밀어내는 방식은 아직 정하지 않았고,
 *   정해지기 전에 임의로 고르지 않는다.
 */
package com.example.mijang.market.pool;

import com.example.mijang.config.MarketProperties;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 구독 종목 풀. <b>30종목이라는 한도를 지키는 유일한 장치다</b>(2.1).
 *
 * <p>같은 종목을 여러 사람이 봐도 구독은 하나다(2.2). {@code Set} 을 쓰는 이유가 그것이다.
 *
 * <p>MVP 의 규칙은 단순하다 — 한도를 넘으면 <b>새 요청을 받지 않는다.</b>
 * 오래된 것을 밀어내는 LRU 는 {@code market-advanced} 범위이고,
 * 그 재설계 결정이 나기 전에 임의로 정하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionPoolManager {

    private final MarketProperties props;

    /** 지금 구독 중인 종목. 입력 순서를 유지해 나중에 LRU 로 바꾸기 쉽게 둔다. */
    private final Set<String> subscribed = new LinkedHashSet<>();

    /**
     * 구독에 넣는다.
     *
     * <p>이미 있으면 아무 일도 하지 않고 true 다 — 중복 구독을 만들지 않는다(2.2).
     *
     * @return 구독 중이면 true. 한도를 넘어 못 넣었으면 false
     */
    public synchronized boolean add(String symbol) {
        if (subscribed.contains(symbol)) {
            return true;
        }
        if (subscribed.size() >= props.getMaxSubscriptions()) {
            log.debug("[구독] 한도 초과로 {} 를 넣지 못했다 ({}/{})",
                    symbol, subscribed.size(), props.getMaxSubscriptions());
            return false;
        }
        subscribed.add(symbol);
        return true;
    }

    /** 구독에서 뺀다. 아무도 보지 않는 종목을 정리할 때 쓴다. */
    public synchronized void remove(String symbol) {
        subscribed.remove(symbol);
    }

    /** 지금 구독 중인 종목들. 벤더에 보낼 목록이다. */
    public synchronized Set<String> current() {
        return Set.copyOf(subscribed);
    }

    /** 구독 중인지. 화면이 "실시간" 배지를 붙일지 판단한다. */
    public synchronized boolean contains(String symbol) {
        return subscribed.contains(symbol);
    }

    /**
     * 구독 목록을 통째로 바꾼다.
     *
     * <p>한도까지만 담고 나머지는 버린다. 버려진 종목은 종가로 보인다(2.1).
     *
     * <p><b>넘기는 값은 "지금 누군가 보고 있는 종목 전부"여야 한다.</b> 한 사용자의
     * 목록만 넘기면 다른 사용자가 보고 있던 종목이 구독에서 빠진다. 그래서 부르는 쪽이
     * {@code SseEmitterRegistry.watchedSymbols()} 와 합쳐 넘긴다.
     *
     * @return 실제로 구독된 종목들
     */
    public synchronized Set<String> replace(Set<String> symbols) {
        subscribed.clear();
        for (String symbol : symbols) {
            if (subscribed.size() >= props.getMaxSubscriptions()) {
                break;
            }
            subscribed.add(symbol);
        }
        return Set.copyOf(subscribed);
    }

    /** 연결이 끊겼을 때 비운다. 재연결하면 처음부터 다시 채운다. */
    public synchronized void clear() {
        subscribed.clear();
    }
}
