package com.example.mijang.market.stream;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.market.pool.SubscriptionPoolManager;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구독 풀 갱신 API. 개발명세서(API) MARKET-001 · 화면 SR-004
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionPoolManager subscriptionPoolManager;

    /** MARKET-001 클라이언트가 보고 있는 종목 목록 전달 */
    @PostMapping("/subscriptions")
    public ApiResponse<Void> replace(@RequestBody Set<String> symbols) {
        subscriptionPoolManager.replace(symbols);
        return ApiResponse.ok(null);
    }
}
