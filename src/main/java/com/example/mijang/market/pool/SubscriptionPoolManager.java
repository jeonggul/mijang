package com.example.mijang.market.pool;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 구독 풀 관리. 화면 노출 종목 + 보유 종목을 벤더 구독 대상으로 유지한다.
 *
 * <p>개발명세서(MVC) · 실시간 시세 · pool
 * <p>기획서 7장은 무료 50종목 한도를 전제로 LRU 를 두었으나, 벤더가 Alpaca 로 바뀌며
 * 구독 종목 수 한도가 사라져 아키텍처명세서 v1.0 에서 LRU 를 걷어냈다.
 * 기획서 부록 E 에 재설계 여부가 아직 [결정 필요] 로 남아 있다.
 */
@Component
public class SubscriptionPoolManager {

    private final Set<String> subscribed = ConcurrentHashMap.newKeySet();

    public void replace(Set<String> symbols) {
        throw new UnsupportedOperationException("TODO MARKET-001: 구독 풀 갱신 후 벤더에 반영");
    }

    public Set<String> current() {
        return Set.copyOf(subscribed);
    }
}
