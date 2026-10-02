/*
 * PasswordVersionRegistry — 비밀번호를 바꾼 계정을 잠깐 기억한다
 *
 * 이 파일이 하는 일
 *   비밀번호가 바뀐 사용자의 새 세대 번호를 access 토큰 수명만큼만 들고 있는다.
 *   필터가 매 요청 DB 를 보지 않고도 "이 토큰은 바뀌기 전 것" 을 가려낼 수 있다.
 */
package com.example.mijang.security;

import com.example.mijang.config.JwtProperties;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 비밀번호를 방금 바꾼 계정의 세대 번호.
 *
 * <p>refresh 는 갱신 길목에서 DB 와 대조해 막는다(8.1.7). 문제는 <b>이미 나간 access</b> 다.
 * 무상태라 서버가 손댈 수 없어 만료까지 최대 30분을 살았다. 비밀번호가 유출돼 급히 바꾼
 * 사람에게는 그 30분이 정확히 위험한 구간이다.
 *
 * <p><b>매 요청 DB 를 보지 않는다는 조건은 그대로 둔다.</b> 8.1.7 이 30분을 받아들인 이유가
 * 그것이었다. 대신 바뀐 계정만 기억한다 — 비밀번호 변경은 드물어서 이 표는 거의 비어 있고,
 * 조회는 해시 한 번이다.
 *
 * <p><b>왜 메모리에 둬도 되는가.</b> {@code JwtProvider} 는 뜰 때마다 새로 만든
 * {@code instanceId} 를 모든 토큰에 담는다. 즉 <b>재시작하면 토큰이 전부 죽는다.</b>
 * 이 표가 프로세스와 함께 사라지는 것과 정확히 같은 수명이라, 표만 비고 토큰은 살아남는
 * 어긋남이 생기지 않는다.
 *
 * <p>여러 대로 늘리면 이 전제가 깨진다. 그때는 {@code instanceId} 도 함께 깨지므로
 * 세션 설계를 통째로 다시 봐야 한다(2.2).
 */
@Component
public class PasswordVersionRegistry {

    /** 언제까지 기억할지와 그때의 세대 번호. */
    private record Entry(int version, Instant expiresAt) {
    }

    private final Map<Long, Entry> changed = new ConcurrentHashMap<>();
    private final JwtProperties props;

    public PasswordVersionRegistry(JwtProperties props) {
        this.props = props;
    }

    /**
     * 비밀번호가 바뀌었음을 적어 둔다.
     *
     * <p>access 수명이 지나면 옛 토큰은 어차피 만료되므로 그때까지만 들고 있으면 된다.
     *
     * @param newVersion 바꾼 뒤의 {@code users.password_version}
     */
    public void record(Long userId, int newVersion) {
        changed.put(userId, new Entry(newVersion, Instant.now().plus(props.getAccessTtl())));
        sweep();
    }

    /**
     * 이 토큰을 아직 믿어도 되는가.
     *
     * <p>기억에 없으면 뜬 뒤로 바꾼 적이 없다는 뜻이라 그대로 통과시킨다.
     *
     * <p>정수 비교인 이유는 8.1.7 과 같다 — 시각으로 견주면 {@code iat} 의 초 단위와
     * 컬럼의 밀리초가 경계에서 어긋난다.
     *
     * @param tokenVersion 토큰에 담긴 {@code pv}. 클레임이 없던 시절 토큰은 0 이다
     */
    public boolean isStale(Long userId, int tokenVersion) {
        Entry entry = changed.get(userId);
        if (entry == null) {
            return false;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            changed.remove(userId);      // 지난 것은 볼 필요가 없다
            return false;
        }
        return tokenVersion < entry.version();
    }

    /** 지나간 것을 치운다. 비밀번호 변경이 드물어 이 표는 몇 건을 넘지 않는다. */
    private void sweep() {
        Instant now = Instant.now();
        changed.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
