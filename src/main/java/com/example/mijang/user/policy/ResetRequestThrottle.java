package com.example.mijang.user.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 재설정 링크 요청 횟수 제한.
 *
 * <p>이 경로는 인증 없이 부를 수 있고 남의 주소를 넣을 수 있다. 막지 않으면 아무나
 * 특정인의 메일함에 계속 메일을 보내게 만들 수 있고, 30분짜리 재설정 토큰도 무한정
 * 찍어 낼 수 있다.
 *
 * <p><b>가입 여부와 무관하게 센다.</b> 가입된 주소만 제한하면 "제한에 걸렸다"는 사실이
 * 곧 가입돼 있다는 뜻이 되어, 응답을 하나로 합쳐 막아 둔 것(8.1.3)이 도로 새어 나간다.
 * 그래서 DB 를 보기 전에 입력값 그대로 센다.
 *
 * <p>메모리에만 둔다. 서버가 재시작하면 초기화되고 여러 대로 늘리면 대(臺)마다 따로
 * 센다. 지금은 한 대이고, Redis 는 실시간 파이프라인 고도화 전까지 들이지 않기로 했다
 * (미장-구현-우선순위). 저장소를 붙일 때 이 클래스만 갈아 끼우면 된다.
 */
@Component
public class ResetRequestThrottle {

    /** 세는 구간. 이 시간이 지나면 기록을 버리고 처음부터 다시 센다. */
    private static final Duration WINDOW = Duration.ofMinutes(10);

    /** 같은 주소로 한 구간에 허용할 횟수. 메일이 늦게 오는 경우를 감안해 한 번은 여유를 둔다. */
    private static final int PER_EMAIL = 3;

    /** 같은 IP 로 한 구간에 허용할 횟수. 가족·회사처럼 주소를 공유하는 경우를 감안한다. */
    private static final int PER_IP = 10;

    /**
     * 기록이 무한정 쌓이지 않게 하는 상한. 넘으면 만료된 것부터 걷어낸다.
     * 걷어내고도 남으면 그때는 전부 비운다 — 세는 것이 목적이지 완벽한 회계가 아니다.
     */
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /** 한 열쇠(주소 또는 IP)의 구간 시작 시각과 횟수. */
    private static final class Counter {
        private volatile Instant windowStart;
        private final AtomicInteger count = new AtomicInteger();

        private Counter(Instant now) {
            this.windowStart = now;
        }
    }

    /**
     * 이번 요청을 받아 줄지 판정하고, 받아 줄 때만 횟수를 올린다.
     *
     * <p>이메일과 IP 를 각각 센다. 둘 중 하나라도 넘으면 막는다.
     *
     * @param email 사용자가 입력한 주소 그대로. 가입돼 있는지는 보지 않는다
     * @param ip    요청 IP. 알 수 없으면 null 을 넘겨도 된다
     * @return 허용하면 true
     */
    public boolean allow(String email, String ip) {
        Instant now = Instant.now();
        sweepIfCrowded(now);

        boolean emailOk = hit("e:" + normalize(email), PER_EMAIL, now);
        boolean ipOk = (ip == null) || hit("i:" + ip, PER_IP, now);
        return emailOk && ipOk;
    }

    /** 대소문자와 앞뒤 공백 때문에 같은 주소가 다른 열쇠가 되지 않게 한다. */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 한 열쇠의 횟수를 올리고 한도 안인지 본다. */
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
