package com.example.mijang.config;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 부팅 후 배포 전에 바꿔야 하는 개발용 기본값을 로그로 알린다(막지는 않는다). */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentReadinessCheck {

    private final JwtProperties jwt;
    private final MailProperties mail;
    private final ExternalApiProperties external;

    /** 개발용 기본값이 남아 있는지 점검해 로그로 남긴다. */
    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        List<String> pending = new ArrayList<>();

        if (!jwt.isCookieSecure()) {
            pending.add("mijang.jwt.cookie-secure=false — 인증 쿠키가 http 로도 나간다."
                    + " https 로 서비스한다면 true 로 바꾼다");
        }
        if (localAddress(mail.getBaseUrl())) {
            pending.add("mijang.mail.base-url=" + mail.getBaseUrl()
                    + " — 비밀번호 재설정 링크가 이 주소로 나간다. 받는 사람은 열 수 없다");
        }
        /* 판정 기준은 Sec.configured() 한 곳에만 둔다. */
        if (!external.sec().configured()) {
            pending.add("mijang.external.sec.user-agent 가 예시 주소다"
                    + " — SEC 는 실제 연락처를 요구하고, 없으면 403 으로 막는다");
        }

        if (pending.isEmpty()) {
            log.info("배포용 설정 점검 — 바꿀 것 없음");
            return;
        }
        log.warn("배포 전에 바꿔야 하는 설정이 {}건 남아 있다. 로컬 개발이라면 정상이다:", pending.size());
        pending.forEach(line -> log.warn("  · {}", line));
    }

    /** 주소가 로컬(외부에서 열 수 없는 곳)을 가리키는지 판정한다. */
    boolean localAddress(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("[::1]");
    }
}
