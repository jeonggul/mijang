package com.example.mijang.user.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** 재설정 링크 요청 횟수를 메모리에서 제한한다 — 가입 여부와 무관하게 세어야 제한 응답으로 가입 여부가 새지 않는다. */
@Component
public class ResetRequestThrottle {

    /** 세는 구간이다 — 지나면 처음부터 다시 센다. */
    private static final Duration WINDOW = Duration.ofMinutes(10);

    /** 같은 주소로 한 구간에 허용할 횟수다. */
    private static final int PER_EMAIL = 3;

    /** 같은 IP로 한 구간에 허용할 횟수다 — 주소를 공유하는 경우를 감안해 여유를 둔다. */
    private static final int PER_IP = 10;

    /** 기록이 무한정 쌓이지 않게 하는 상한이다 — 넘으면 만료분부터 걷어내고, 그래도 남으면 전부 비운다. */
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /** 한 열쇠(주소 또는 IP)의 구간 시작 시각과 횟수를 담는다. */
    private static final class Counter {
        private volatile Instant windowStart;
        private final AtomicInteger count = new AtomicInteger();

        private Counter(Instant now) {
            this.windowStart = now;
        }
    }

    /** 이번 요청을 받아 줄지 판정하고 받아 줄 때만 횟수를 올린다 — 이메일과 IP(null 허용) 중 하나라도 한도를 넘으면 막는다. */
    public boolean allow(String email, String ip) {
        Instant now = Instant.now();
        sweepIfCrowded(now);

        boolean emailOk = hit("e:" + normalize(email), PER_EMAIL, now);
        boolean ipOk = (ip == null) || hit("i:" + ip, PER_IP, now);
        return emailOk && ipOk;
    }

    /** 대소문자·공백 차이로 같은 주소가 다른 열쇠가 되지 않게 정규화한다. */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 한 열쇠의 횟수를 올리고 한도 안인지 판정한다. */
    private boolean hit(String key, int limit, Instant now) {
        Counter c = counters.computeIfAbsent(key, k -> new Counter(now));
        synchronized (c) {
            // 구간이 지났으면 처음부터 다시 센다
            if (Duration.between(c.windowStart, now).compareTo(WINDOW) >= 0) {
                c.windowStart = now;
                c.count.set(0);
            }
            if (c.count.get() >= limit) {
                return false;
            }
            c.count.incrementAndGet();
            return true;
        }
    }

    /** 기록이 너무 많아지면 만료된 것을 걷어낸다. */
    private void sweepIfCrowded(Instant now) {
        if (counters.size() < MAX_ENTRIES) {
            return;
        }
        counters.entrySet().removeIf(
                e -> Duration.between(e.getValue().windowStart, now).compareTo(WINDOW) >= 0);
        if (counters.size() >= MAX_ENTRIES) {
            counters.clear();
        }
    }
}
