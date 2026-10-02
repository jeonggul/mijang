package com.example.mijang.security;

import com.example.mijang.config.JwtProperties;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 비밀번호를 바꾼 계정의 세대 번호를 access 수명 동안만 메모리에 기억한다(단일 인스턴스 전제, 2.2). */
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

    /** 비밀번호가 바뀌었음을 access 수명만큼 적어 둔다. */
    public void record(Long userId, int newVersion) {
        changed.put(userId, new Entry(newVersion, Instant.now().plus(props.getAccessTtl())));
        sweep();
    }

    /** 토큰의 세대가 비밀번호 변경 전 것인지 판정한다. */
    public boolean isStale(Long userId, int tokenVersion) {
        Entry entry = changed.get(userId);
        if (entry == null) {
            return false;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            changed.remove(userId);
            return false;
        }
        return tokenVersion < entry.version();
    }

    /** 수명이 지난 항목을 치운다. */
    private void sweep() {
        Instant now = Instant.now();
        changed.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
