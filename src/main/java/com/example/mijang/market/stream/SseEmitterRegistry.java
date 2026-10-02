package com.example.mijang.market.stream;

import com.example.mijang.market.dto.QuoteResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 열려 있는 SSE 연결 목록을 들고 있다가 받은 시세를 그 종목을 보고 있는 연결에만 보낸다. */
@Slf4j
@Component
public class SseEmitterRegistry {

    /** 연결 하나가 보고 있는 것. seq 는 붙은 차례다. */
    private record Watch(long seq, Set<String> symbols) { }

    /** 연결 → 그 연결이 보고 있는 종목들. */
    private final Map<SseEmitter, Watch> watching = new ConcurrentHashMap<>();

    /** 붙은 차례를 매기는 번호. 30칸을 넘겨 자를 때 먼저 온 순서를 지키는 기준이다. */
    private final java.util.concurrent.atomic.AtomicLong sequence =
            new java.util.concurrent.atomic.AtomicLong();

    /** 연결이 빠질 때마다 부를 것. 구독 목록을 다시 맞추는 데 쓴다. */
    private volatile Runnable onRelease = () -> { };

    /** 연결이 빠질 때 할 일을 정한다. 걸지 않으면 구독 풀이 줄지 않아 30칸 한도가 묶인다. */
    public void onRelease(Runnable action) {
        this.onRelease = action == null ? () -> { } : action;
    }

    /** 연결을 등록하고 끝나면 스스로 빠지도록 콜백을 건다. */
    public void register(SseEmitter emitter, Set<String> symbols) {
        watching.put(emitter, new Watch(sequence.incrementAndGet(), Set.copyOf(symbols)));
        emitter.onCompletion(() -> release(emitter));
        emitter.onTimeout(() -> release(emitter));
        emitter.onError(e -> release(emitter));
    }

    /** 연결 하나를 걷어내고 구독 목록을 다시 맞춘다. 콜백이 두 번 와도 한 번만 처리한다. */
    private void release(SseEmitter emitter) {
        if (watching.remove(emitter) != null) {
            onRelease.run();
        }
    }

    /** 한 종목의 시세를 보고 있는 연결들에 보낸다. 보내다 실패한 연결은 그 자리에서 걷어낸다. */
    public void broadcast(QuoteResponse quote) {
        watching.forEach((emitter, watch) -> {
            if (!watch.symbols().contains(quote.symbol())) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("quote").data(quote));
            } catch (IOException | IllegalStateException e) {
                // 이미 닫힌 연결이다. 조용히 정리하고 구독 목록도 다시 맞춘다.
                release(emitter);
                emitter.complete();
            }
        });
    }

    /** 주기적으로 심박을 보내 죽은 연결을 걷어낸다. 이게 없으면 30칸 구독 자리가 유령 연결에 묶인다. */
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

    /** 지금 누군가 보고 있는 종목 전부를 먼저 붙은 순서로 반환한다. 30칸을 넘으면 뒤에서부터 잘린다. */
    public Set<String> watchedSymbols() {
        return watching.values().stream()
                .sorted(java.util.Comparator.comparingLong(Watch::seq))
                .flatMap(w -> w.symbols().stream())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** 열려 있는 연결 수를 반환한다. */
    public int connectionCount() {
        return watching.size();
    }
}
