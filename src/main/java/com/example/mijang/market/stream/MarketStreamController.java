/*
 * MarketStreamController — 브라우저로 시세를 밀어 주는 곳
 *
 * 이 파일이 하는 일
 *   브라우저가 여기에 붙으면 시세가 올 때마다 밀어 준다.
 *   웹소켓이 아니라 SSE 를 쓴다 — 시세는 서버가 일방적으로 보내기만 하면 되고,
 *   끊겼을 때 브라우저가 알아서 다시 붙어 준다.
 *   로그인하지 않아도 볼 수 있다. 종목 시세는 공개 정보다.
 */
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

/**
 * 실시간 시세 스트림. 개발명세서(API) PRICE-01 · 화면 SR-003·SR-005
 *
 * <p>브라우저에는 웹소켓이 아니라 SSE 로 보낸다(2.3) — 시세는 서버가 일방적으로
 * 밀어 주기만 하면 되고, 끊겼을 때 브라우저가 알아서 다시 붙는다.
 *
 * <p>비로그인도 볼 수 있다. 종목 시세는 공개 정보다([[미장-기획서]] 4장).
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketStreamController {

    private final com.example.mijang.market.pool.SubscriptionPoolManager pool;
    private final SseEmitterRegistry registry;

    /** 벤더 수신부. 설정으로 꺼 둘 수 있어 없을 수도 있다 */
    private final org.springframework.beans.factory.ObjectProvider<
            com.example.mijang.market.client.AlpacaWebSocketClient> stream;
    private final QuoteService quoteService;
    private final MarketProperties props;

    /**
     * 연결이 빠질 때 구독 목록을 다시 맞추도록 걸어 둔다.
     *
     * <p>이걸 걸지 않으면 풀은 넣기만 하고 줄지 않는다. 화면을 닫아도 그 종목이 벤더
     * 구독에 남고, 서로 다른 종목 30개가 쌓인 뒤로는 새로 연 종목이 한도에 걸려
     * 재시작 전까지 종가만 보인다.
     *
     * <p>{@code watchedSymbols()} 는 <b>지금 열려 있는 연결 전부</b>가 보고 있는 종목이라,
     * 한 사람이 닫아도 같은 종목을 보고 있는 다른 사람의 구독은 그대로 남는다.
     */
    @jakarta.annotation.PostConstruct
    void bindRelease() {
        registry.onRelease(() -> {
            pool.replace(registry.watchedSymbols());
            stream.ifAvailable(client -> client.resubscribe());
        });
    }

    /**
     * 시세 스트림을 연다.
     *
     * <p>연결하자마자 <b>현재 값을 한 번 보낸다.</b> 그러지 않으면 다음 체결이 올 때까지
     * 화면이 비어 있고, 장 마감 후에는 영원히 비어 있다.
     *
     * @param symbols 쉼표로 구분한 티커들
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String symbols) {
        Set<String> targets = parse(symbols);

        /* 첫 값을 먼저 읽는다. 등록보다 앞이어야 한다 —
           등록해 놓고 DB 를 읽다가 예외가 나면 컨트롤러가 그대로 튕기고, 그 emitter 는
           스프링에 돌려주지 못한 채 목록에만 남는다. 돌려주지 못한 emitter 는
           onCompletion·onTimeout·onError 가 아예 걸리지 않고, 심박을 보내도 예외 없이
           삼켜져서(초기화 전이라 보낼 곳이 없다) 걷어낼 방법이 없다. 그렇게 남은 유령이
           30칸을 차지하면 아무도 실시간을 못 받는다 */
        List<QuoteResponse> first = quoteService.quotes(List.copyOf(targets));

        SseEmitter emitter = new SseEmitter(props.getSseTimeout().toMillis());
        registry.register(emitter, targets);

        /* 새 화면이 붙었다. 이 종목들을 풀에 넣고 벤더 구독을 맞춘다.
           한도를 넘으면 넘는 만큼은 들어가지 않고, 그 종목은 종가로만 나간다(2.1) */
        targets.forEach(pool::add);
        stream.ifAvailable(client -> client.resubscribe());

        // 실시간이 없으면 종가가 나간다(2.8)
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

    /**
     * 현재가 조회. 스트림 없이 한 번만 받고 싶을 때 쓴다.
     *
     * <p>관심종목 목록처럼 갱신이 잦지 않은 화면이 이걸 쓴다.
     *
     * <p><b>여기에는 개수 상한을 걸지 않는다.</b> 상한은 벤더 구독 자리 30칸 때문에 두는 것인데
     * 이 경로는 DB 만 읽어 자리를 쓰지 않는다. 상한을 걸면 관심종목이 40개인 사람은 열 개가
     * 영영 "—" 로 남는다.
     */
    @GetMapping("/quotes")
    public ApiResponse<List<QuoteResponse>> quotes(@RequestParam String symbols) {
        return ApiResponse.ok(quoteService.quotes(List.copyOf(Tickers.parseCsv(symbols))));
    }

    /**
     * 티커 목록을 읽는다.
     *
     * <p>모양이 맞는 것만 통과시킨다. 이 값은 벤더에 보내는 제어 프레임에 실리므로,
     * 따옴표나 대괄호가 섞인 문자열을 그대로 넘기면 프레임 구조를 바깥에서 정하게 된다.
     * 이 경로는 로그인 없이 부를 수 있어 더 그렇다.
     *
     * <p>규칙은 {@link Tickers} 에 모아 두었다 — 구독 갱신 입구도 같은 것을 쓴다.
     *
     * <p>개수도 한도까지만 받는다. 구독 자리는 서버 전체가 30칸을 나눠 쓰는데, 이 경로는
     * 로그인 없이 부를 수 있어 한 요청이 30개를 실어 보내면 자리를 혼자 다 차지한다.
     * 넘친 종목은 종가로 나간다(2.1).
     */
    private Set<String> parse(String symbols) {
        return Tickers.parseCsv(symbols, props.getMaxSubscriptions());
    }
}
