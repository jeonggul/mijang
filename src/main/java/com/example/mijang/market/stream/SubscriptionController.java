/*
 * SubscriptionController — 보고 있는 종목을 알리는 입구
 *
 * 이 파일이 하는 일
 *   화면이 "지금 이 종목들을 보고 있다"고 알려 오면 구독 목록을 그것으로 바꾼다.
 *   스트림으로 붙은 화면은 연결 자체가 이미 그 정보를 담고 있어 부를 필요가 없다.
 *   폴링으로 도는 화면을 위한 경로다.
 */
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

/**
 * 클라이언트가 "지금 이 종목들을 보고 있다"고 알리는 경로. 개발명세서(API) PRICE-01
 *
 * <p>SSE 로 붙지 않고 폴링하는 화면을 위한 입구다. 스트림으로 붙는 화면은
 * 연결 자체가 이미 그 정보를 담고 있어 따로 부를 필요가 없다.
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionPoolManager subscriptionPoolManager;
    private final SseEmitterRegistry registry;

    /**
     * 벤더 수신부. 설정으로 꺼 둘 수 있어 없을 수도 있다.
     *
     * <p>직접 주입받지 않고 {@code ObjectProvider} 로 받는 이유 — 수신부는 구독 풀을
     * 읽고 여기는 풀을 바꾼다. 서로 직접 붙이면 순환이 된다.
     */
    private final org.springframework.beans.factory.ObjectProvider<
            com.example.mijang.market.client.AlpacaWebSocketClient> stream;

    /**
     * 구독 대상을 갱신한다.
     *
     * <p>받은 목록으로 <b>덮어쓰지 않고</b> 지금 열려 있는 SSE 연결들이 보고 있는 종목과
     * 합쳐서 넘긴다. 한 사용자의 목록만 그대로 넣으면 다른 사용자가 보던 종목이 빠진다.
     *
     * <p>한도를 넘는 만큼은 버려지고 그 종목은 종가로 보인다(2.1).
     *
     * <p>티커 모양을 검사한다({@link Tickers}). 여기서 받은 값도 결국 벤더 제어 프레임에
     * 실리고 30칸뿐인 공용 자리를 차지하므로, 스트림 입구와 같은 규칙을 써야 한다.
     */
    @PostMapping("/subscriptions")
    public ApiResponse<Void> replace(@RequestBody Set<String> symbols) {
        /* LinkedHashSet 이어야 한다. watchedSymbols() 가 붙은 순서대로 내주는데
           HashSet 에 담으면 그 순서가 흩어진다. 30칸을 넘겨 자를 때 누가 살아남을지가
           매번 뒤바뀌어, 먼저 보고 있던 사람의 시세가 남의 폴링 한 번에 끊긴다 */
        Set<String> merged = new LinkedHashSet<>(registry.watchedSymbols());
        merged.addAll(Tickers.clean(symbols));
        subscriptionPoolManager.replace(merged);
        /* 풀이 바뀌었으니 벤더 구독도 맞춘다. 이게 없으면 목록만 바뀌고 값은 안 온다 */
        stream.ifAvailable(client -> client.resubscribe());
        return ApiResponse.ok(null);
    }

    /**
     * 실시간 연결 상태. {@code PRICE-01}
     *
     * <p>화면이 "15분 지연" 을 띄울 때 그것이 장 시간 밖이라 그런지, 연결이 끊겨서 그런지
     * 구분할 길이 없어 넣었다. 값이 아니라 상태를 보는 곳이라 로그인 없이 연다.
     */
    @GetMapping("/status")
    public ApiResponse<String> status() {
        return ApiResponse.ok(stream.getIfAvailable() == null
                ? "실시간 수신이 꺼져 있다 (mijang.market.stream-enabled=false)"
                : stream.getIfAvailable().describeState());
    }
}
