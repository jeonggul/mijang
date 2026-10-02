package com.example.mijang.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** 로그인 실패를 이메일·IP 별로 세어 잠근다. 단일 서버 메모리 집계라 다중화 시 Redis 이관이 필요하다. */
@Service
public class LoginAttemptService {

    /** 한 이메일이 이 횟수를 넘겨 실패하면 잠근다. */
    private static final int EMAIL_MAX = 5;

    /** 한 IP 가 이 횟수를 넘기면 잠근다. 공용 IP 를 감안해 넉넉히 둔다. */
    private static final int IP_MAX = 30;

    /** 잠금·집계 창. 이 시간이 지나면 실패 기록이 사라진다. */
    private static final Duration WINDOW = Duration.ofMinutes(15);

    /** 창이 지난 기록을 걷어내는 간격. 매 요청 전체를 훑으면 비싸다. */
    private static final Duration SWEEP_EVERY = Duration.ofMinutes(5);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private volatile Instant lastSweep = Instant.now();

    /** 한 열쇠(이메일 또는 IP)의 실패 누적. 만들어지는 순간이 첫 실패다. */
    private static final class Attempt {
        int count = 1;
        final Instant firstAt = Instant.now();
    }

    /** 이메일·IP 중 하나라도 한도를 넘었으면 로그인을 막는다. */
    public boolean isBlocked(String email, String ip) {
        sweepIfDue();
        return over("e:" + normalize(email), EMAIL_MAX) || over("i:" + ip, IP_MAX);
    }

    /** 실패를 이메일·IP 두 열쇠에 각각 센다. */
    public void recordFailure(String email, String ip) {
        bump("e:" + normalize(email));
        bump("i:" + ip);
    }

    /** 성공한 이메일의 기록만 지운다. IP 기록은 한도 초기화 악용을 막기 위해 남긴다. */
    public void recordSuccess(String email) {
        attempts.remove("e:" + normalize(email));
    }

    private boolean over(String key, int max) {
        Attempt a = attempts.get(key);
        if (a == null) {
            return false;
        }
        if (expired(a)) {
            attempts.remove(key);
            return false;
        }
        return a.count >= max;
    }

    private void bump(String key) {
        attempts.compute(key, (k, a) -> {
            if (a == null || expired(a)) {
                return new Attempt();       // 첫 실패는 1 로 시작한다
            }
            a.count++;
            return a;
        });
    }

    private static boolean expired(Attempt a) {
        return a.firstAt.plus(WINDOW).isBefore(Instant.now());
    }

    /** 창이 지난 기록을 걷어낸다. 안 하면 이메일마다 한 칸씩 영원히 쌓인다. */
    private void sweepIfDue() {
        if (lastSweep.plus(SWEEP_EVERY).isAfter(Instant.now())) {
            return;
        }
        lastSweep = Instant.now();
        attempts.entrySet().removeIf(e -> expired(e.getValue()));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
