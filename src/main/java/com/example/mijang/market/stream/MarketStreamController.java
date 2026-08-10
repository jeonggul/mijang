package com.example.mijang.market.stream;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.market.cache.QuoteCacheService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실시간 시세 스트림 API. 개발명세서(API) MARKET-002, MARKET-003 · 화면 SR-004
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketStreamController {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final QuoteCacheService quoteCacheService;

    /** MARKET-002 SSE 구독 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        throw new UnsupportedOperationException("TODO MARKET-002: 로그인 사용자 기준 emitter 등록");
    }

    /** MARKET-003 현재가 스냅샷 */
    @GetMapping("/quotes")
    public ApiResponse<Map<String, BigDecimal>> quotes(@RequestParam("symbols") List<String> symbols) {
        return ApiResponse.ok(quoteCacheService.quotes(symbols));
    }
}
