package com.example.mijang.market.pool;

import com.example.mijang.config.MarketProperties;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 구독 종목 풀을 관리한다. 30종목 한도를 지키는 유일한 장치이며, 한도를 넘으면 새 요청을 받지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionPoolManager {

    private final MarketProperties props;

    /** 지금 구독 중인 종목. 입력 순서를 유지한다. */
    private final Set<String> subscribed = new LinkedHashSet<>();

    /** 구독에 넣는다. 이미 있으면 true, 한도를 넘어 못 넣으면 false 를 반환한다. */
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

    /** 구독에서 뺀다. */
    public synchronized void remove(String symbol) {
        subscribed.remove(symbol);
    }

    /** 지금 구독 중인 종목들을 반환한다. */
    public synchronized Set<String> current() {
        return Set.copyOf(subscribed);
    }

    /** 구독 중인지 반환한다. */
    public synchronized boolean contains(String symbol) {
        return subscribed.contains(symbol);
    }

    /** 구독 목록을 통째로 바꾸고 실제 구독된 종목들을 반환한다. 넘기는 값은 지금 누군가 보고 있는 종목 전부여야 한다. */
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

    /** 구독 목록을 비운다. 연결이 끊겼을 때 쓴다. */
    public synchronized void clear() {
        subscribed.clear();
    }
}
