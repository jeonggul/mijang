package com.example.mijang.market.stream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 사용자별 SseEmitter 보관소. 서버 → 클라이언트는 SSE 단방향 푸시다.
 *
 * <p>개발명세서(MVC) · 실시간 시세 · stream
 */
@Component
public class SseEmitterRegistry {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long userId, long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitters.put(userId, emitter);
        return emitter;
    }

    public void remove(Long userId) {
        emitters.remove(userId);
    }

    public int size() {
        return emitters.size();
    }
}
