/*
 * SseEmitterRegistry — 열려 있는 브라우저 연결 목록
 *
 * 이 파일이 하는 일
 *   지금 시세를 받고 있는 연결들을 들고 있다가, 값이 오면
 *   그 종목을 보고 있는 연결에만 보낸다.
 *   연결 하나가 여러 종목을 볼 수 있고 종목 하나를 여러 연결이 볼 수 있어
 *   양쪽 관계를 다 다뤄야 한다.
 */
package com.example.mijang.market.stream;

import com.example.mijang.market.dto.QuoteResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 열려 있는 SSE 연결 목록. 받은 시세를 <b>그 종목을 보고 있는 연결에만</b> 보낸다(2.2).
 *
 * <p>연결 하나가 여러 종목을 볼 수 있고, 종목 하나를 여러 연결이 볼 수 있다.
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    /** 연결 하나가 보고 있는 것. {@code seq} 는 붙은 차례다 */
    private record Watch(long seq, Set<String> symbols) { }

    /** 연결 → 그 연결이 보고 있는 종목들. */
    private final Map<SseEmitter, Watch> watching = new ConcurrentHashMap<>();

    /**
     * 붙은 차례를 매기는 번호.
     *
     * <p>구독은 30칸뿐이라 넘치면 누군가는 빠져야 한다. 그 기준이 <b>먼저 온 순서</b>여야
     * 한다. 순서 없는 집합을 그대로 넘기면 자를 때마다 살아남는 종목이 뒤바뀌어,
     * 연결 하나가 끊길 때마다 엉뚱한 사람의 시세가 멈췄다 살았다 한다.
     */
    private final java.util.concurrent.atomic.AtomicLong sequence =
            new java.util.concurrent.atomic.AtomicLong();

    /** 연결이 빠질 때마다 부를 것. 구독 목록을 다시 맞추는 데 쓴다. */
    private volatile Runnable onRelease = () -> { };

    /**
     * 연결이 빠질 때 할 일을 정한다.
     *
     * <p>구독 풀은 <b>넣기만 하면 줄지 않는다.</b> 화면을 닫아도 그 종목이 벤더 구독에
     * 남아 있으면, 서로 다른 종목 30개가 쌓인 뒤로는 새로 연 종목이 한도에 걸려
     * 영원히 실시간을 받지 못한다. 연결이 빠질 때마다 여기서 풀을 다시 맞춘다.
     */
    public void onRelease(Runnable action) {
        this.onRelease = action == null ? () -> { } : action;
    }

    /**
     * 연결을 등록한다.
     *
     * <p>끝나거나 시간이 다 되면 스스로 빠지도록 콜백을 건다. 걸지 않으면
     * 죽은 연결이 계속 쌓여 보낼 때마다 예외가 난다.
     */
    public void register(SseEmitter emitter, Set<String> symbols) {
        watching.put(emitter, new Watch(sequence.incrementAndGet(), Set.copyOf(symbols)));
        emitter.onCompletion(() -> release(emitter));
        emitter.onTimeout(() -> release(emitter));
        emitter.onError(e -> release(emitter));
    }

    /**
     * 연결 하나를 걷어내고 구독 목록을 다시 맞춘다.
     *
     * <p>이미 빠진 연결이면 아무 일도 하지 않는다 — 콜백이 두 번 올 수 있다.
     */
    private void release(SseEmitter emitter) {
        if (watching.remove(emitter) != null) {
            onRelease.run();
        }
    }

    /**
     * 한 종목의 시세를 보고 있는 연결들에 보낸다.
     *
     * <p>보내다 실패한 연결은 <b>그 자리에서 걷어낸다.</b> 브라우저를 닫아도
     * 서버는 바로 알지 못해 죽은 연결이 남는다.
     */
    public void broadcast(QuoteResponse quote) {
        watching.forEach((emitter, watch) -> {
            if (!watch.symbols().contains(quote.symbol())) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("quote").data(quote));
            } catch (IOException | IllegalStateException e) {
                // 이미 닫힌 연결이다. 조용히 정리하고 구독 목록도 다시 맞춘다
                release(emitter);
                emitter.complete();
            }
        });
    }

    /**
     * 살아 있는지 확인차 주기적으로 한 글자 보낸다.
     *
     * <p>브라우저를 닫아도 서버는 바로 알지 못한다. <b>뭔가 써 봐야</b> 끊긴 것을 안다.
     * 그래서 시세가 흐를 때는 {@link #broadcast} 가 알아서 정리하지만, 체결이 뜸한
     * 종목만 열려 있으면 죽은 연결이 그대로 남는다. 그러면 구독 자리 30칸이 유령에게
     * 묶여, 새로 연 종목은 한도에 걸려 실시간을 못 받는다.
     *
     * <p>보내는 것은 주석 줄이라 화면에는 아무 일도 일어나지 않는다.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        watching.keySet().forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                release(emitter);
                emitter.complete();
            }
        });
    }

    /** 지금 누군가 보고 있는 종목 전부. 구독 대상을 정할 때 쓴다. */
    public Set<String> watchedSymbols() {
        /* 먼저 붙은 연결의 종목이 앞에 온다. 30칸을 넘으면 뒤에서부터 잘리므로
           이 순서가 곧 "누가 실시간을 유지하는가" 를 정한다 */
        return watching.values().stream()
                .sorted(java.util.Comparator.comparingLong(Watch::seq))
                .flatMap(w -> w.symbols().stream())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** 열려 있는 연결 수. 관리자 화면과 로그에 쓴다. */
    public int connectionCount() {
        return watching.size();
    }
}
