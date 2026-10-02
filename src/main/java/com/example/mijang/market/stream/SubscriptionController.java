package com.example.mijang.market.stream;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.market.domain.Tickers;
import com.example.mijang.market.pool.SubscriptionPoolManager;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 폴링 화면이 보고 있는 종목을 알려 구독 목록을 갱신하는 API를 제공한다. */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionPoolManager subscriptionPoolManager;
    private final SseEmitterRegistry registry;

    /** 벤더 수신부. 설정으로 꺼 둘 수 있고, 순환 참조를 피하려고 ObjectProvider 로 받는다. */
    private final org.springframework.beans.factory.ObjectProvider<
            com.example.mijang.market.client.AlpacaWebSocketClient> stream;

    /** 구독 대상을 갱신한다. 받은 목록으로 덮어쓰지 않고 열려 있는 SSE 연결의 종목과 합쳐서 넘긴다. */
    @PostMapping("/subscriptions")
    public ApiResponse<Void> replace(@RequestBody Set<String> symbols) {
        // LinkedHashSet 이어야 한다 — 순서가 흩어지면 30칸을 넘겨 자를 때 살아남는 종목이 매번 뒤바뀐다.
        Set<String> merged = new LinkedHashSet<>(registry.watchedSymbols());
        merged.addAll(Tickers.clean(symbols));
        subscriptionPoolManager.replace(merged);
        // 풀이 바뀌었으니 벤더 구독도 맞춘다. 이게 없으면 목록만 바뀌고 값은 안 온다.
        stream.ifAvailable(client -> client.resubscribe());
        return ApiResponse.ok(null);
    }

    /** 실시간 연결 상태를 반환한다. */
    @GetMapping("/status")
    public ApiResponse<String> status() {
        return ApiResponse.ok(stream.getIfAvailable() == null
                ? "실시간 수신이 꺼져 있다 (mijang.market.stream-enabled=false)"
                : stream.getIfAvailable().describeState());
    }
}
