package com.example.mijang.market.stream;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.config.MarketProperties;
import com.example.mijang.market.domain.Tickers;
import com.example.mijang.market.dto.QuoteResponse;
import com.example.mijang.market.service.QuoteService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 브라우저에 SSE 로 실시간 시세를 밀어 준다. 비로그인도 볼 수 있다. */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketStreamController {

    private final com.example.mijang.market.pool.SubscriptionPoolManager pool;
    private final SseEmitterRegistry registry;

    /** 벤더 수신부. 설정으로 꺼 둘 수 있어 없을 수도 있다. */
    private final org.springframework.beans.factory.ObjectProvider<
            com.example.mijang.market.client.AlpacaWebSocketClient> stream;
    private final QuoteService quoteService;
    private final MarketProperties props;

    /** 연결이 빠질 때 구독 목록을 다시 맞추도록 걸어 둔다. 걸지 않으면 풀이 넣기만 하고 줄지 않아 30칸 한도가 유령 구독에 묶인다. */
    @jakarta.annotation.PostConstruct
    void bindRelease() {
        registry.onRelease(() -> {
            pool.replace(registry.watchedSymbols());
            stream.ifAvailable(client -> client.resubscribe());
        });
    }

    /** 시세 스트림을 열고 연결 직후 현재 값을 한 번 보낸다. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String symbols) {
        Set<String> targets = parse(symbols);

        // 첫 값 읽기는 등록보다 앞이어야 한다 — 등록 후 예외가 나면 걷어낼 수 없는 유령 emitter 가 30칸을 차지한다.
        List<QuoteResponse> first = quoteService.quotes(List.copyOf(targets));

        SseEmitter emitter = new SseEmitter(props.getSseTimeout().toMillis());
        registry.register(emitter, targets);

        // 종목들을 풀에 넣고 벤더 구독을 맞춘다. 한도를 넘는 종목은 종가로만 나간다.
        targets.forEach(pool::add);
        stream.ifAvailable(client -> client.resubscribe());

        for (QuoteResponse quote : first) {
            try {
                emitter.send(SseEmitter.event().name("quote").data(quote));
            } catch (Exception e) {
                emitter.completeWithError(e);
                return emitter;
            }
        }
        return emitter;
    }

    /** 현재가를 스트림 없이 한 번만 조회한다. DB 만 읽으므로 개수 상한을 걸지 않는다. */
    @GetMapping("/quotes")
    public ApiResponse<List<QuoteResponse>> quotes(@RequestParam String symbols) {
        return ApiResponse.ok(quoteService.quotes(List.copyOf(Tickers.parseCsv(symbols))));
    }

    /** 티커 목록을 읽는다. 모양 검사와 30칸 한도는 Tickers 규칙을 따른다. */
    private Set<String> parse(String symbols) {
        return Tickers.parseCsv(symbols, props.getMaxSubscriptions());
    }
}
