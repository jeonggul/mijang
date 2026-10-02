/*
 * DeploymentReadinessCheck — 개발용 기본값으로 뜨고 있으면 알려 주는 곳
 *
 * 이 파일이 하는 일
 *   부팅이 끝나면 "배포 전에 바꿔야 하는" 설정 몇 개를 훑어 로그에 남긴다.
 *   막지는 않는다 — 로컬에서는 그 값이 맞고, 뜨지 않으면 개발을 못 한다.
 *
 *   왜 필요한가
 *     설정 파일에는 이미 "배포 시 바꾼다" 는 주석이 달려 있었다. 주석은 파일을 열어
 *     본 사람에게만 보인다. 배포는 파일을 열지 않고 하는 일이라, 그 주석이 지켜지는지
 *     확인할 방법이 없었다. 부팅 로그는 배포할 때 반드시 보게 된다.
 */
package com.example.mijang.config;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 개발용 기본값 점검. 부팅이 끝난 뒤 한 번 돈다.
 *
 * <p>세 값이 배포에서 그대로 남으면 각각 이렇게 된다.
 *
 * <ul>
 *   <li>{@code cookie-secure=false} — 인증 쿠키가 http 로도 나간다</li>
 *   <li>{@code mail.base-url} 이 localhost — 비밀번호 재설정 링크를 받는 사람이 열 수 없다</li>
 *   <li>SEC User-Agent 가 예시 주소 — SEC 가 요청을 막는다</li>
 * </ul>
 *
 * <p><b>막지 않고 알리기만 한다.</b> 로컬에서는 이 값들이 맞고, 여기서 부팅을 막으면
 * 개발이 안 된다. 판단은 배포하는 사람이 하고, 이 로그는 판단할 거리를 준다.
 *
 * <p>2026-09-03 점검 6장에서 셋 다 개발 기본값 그대로였다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentReadinessCheck {

    private final JwtProperties jwt;
    private final MailProperties mail;
    private final ExternalApiProperties external;

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
        /* 판정은 Sec.configured() 가 이미 들고 있다. 여기서 다시 적으면 두 곳이 갈린다 */
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

    /** 이 주소가 내 컴퓨터를 가리키는가. 밖에서는 열리지 않는 주소들이다. */
    boolean localAddress(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("[::1]");
    }
}
