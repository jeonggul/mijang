/*
 * LoginAttemptService — 로그인 시도 제한
 *
 * 이 파일이 하는 일
 *   짧은 시간에 실패가 몰리면 잠시 막는다. 비밀번호 재설정에는 재전송 간격이 있는데
 *   정작 로그인은 무제한이라 대입을 그냥 받아 주고 있었다.
 *
 *   왜 이메일과 IP 를 둘 다 세는가
 *     이메일만 세면 한 곳에서 계정을 바꿔 가며 흔한 비밀번호를 뿌리는 공격이 통과한다.
 *     IP 만 세면 회사·학교처럼 여럿이 한 IP 를 쓰는 곳에서 남 때문에 막힌다.
 *     둘 중 하나라도 넘으면 막되, IP 한도를 이메일보다 넉넉히 둔다.
 *
 *   왜 메모리인가
 *     서버가 한 대다. 여러 대가 되면 각 대가 따로 세어 한도가 대수만큼 늘어나므로
 *     그때는 Redis 로 옮겨야 한다 — 그 전제를 여기 적어 둔다.
 *
 *   막힌 동안에도 응답은 "이메일 또는 비밀번호가 올바르지 않습니다" 와 같은 자리에서
 *   나온다. 잠금 사실을 알려 주면 어떤 이메일이 실재하는지가 드러난다.
 */
package com.example.mijang.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** 로그인 실패 횟수를 세어 잠근다. */
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

    /**
     * 지금 로그인을 받아도 되는지.
     *
     * <p>이메일과 IP 중 <b>하나라도</b> 한도를 넘으면 막는다.
     */
    public boolean isBlocked(String email, String ip) {
        sweepIfDue();
        return over("e:" + normalize(email), EMAIL_MAX) || over("i:" + ip, IP_MAX);
    }

    /** 실패했다. 두 열쇠 모두에 한 번씩 센다. */
    public void recordFailure(String email, String ip) {
        bump("e:" + normalize(email));
        bump("i:" + ip);
    }

    /**
     * 성공했다. 그 이메일의 기록만 지운다.
     *
     * <p>IP 기록은 남긴다 — 계정 하나를 맞혔다고 그 IP 가 뿌리던 시도까지 없던 일이 되면
     * 공격자가 자기 계정으로 한 번 로그인해 한도를 계속 초기화할 수 있다.
     */
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
