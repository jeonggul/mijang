package com.example.mijang.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 토큰 설정. application.properties 의 mijang.jwt.* 를 받는다.
 *
 * <p>secret 만 application-secret.properties 에 있고 나머지는 공개 설정이다.
 */
@ConfigurationProperties(prefix = "mijang.jwt")
public class JwtProperties {

    /** HS256 서명 키. 32바이트 미만이면 JwtProvider 생성자가 막는다. */
    private String secret;
    /** 토큰 발급자. 파싱할 때 이 값이 일치하는지도 함께 본다. */
    private String issuer = "mijang";
    /** access 수명. 명세서 1.3 의 30분. */
    private Duration accessTtl = Duration.ofMinutes(30);
    /** refresh 수명. 명세서 1.3 의 14일. */
    private Duration refreshTtl = Duration.ofDays(14);


    /** access 를 담을 쿠키 이름. 화면 인증에 쓰인다. */
    private String accessCookie = "MIJANG_AT";
    /** refresh 를 담을 쿠키 이름. 갱신에만 쓰인다. */
    private String refreshCookie = "MIJANG_RT";
    /** true 면 https 에서만 쿠키가 전송된다. 로컬은 http 라 false. */
    private boolean cookieSecure = false;

    // 아래는 스프링이 값을 넣고 꺼내기 위한 접근자다. 별도 로직은 없다.

    /** 서명 키를 읽는다. JwtProvider 생성자 외에는 부르지 않는다. */
    public String getSecret() { return secret; }
    /** mijang.jwt.secret 주입. 값은 application-secret.properties 에서 온다. */
    public void setSecret(String secret) { this.secret = secret; }

    /** 발급자를 읽는다. 토큰을 만들 때와 검증할 때 모두 쓰인다. */
    public String getIssuer() { return issuer; }
    /** mijang.jwt.issuer 주입. */
    public void setIssuer(String issuer) { this.issuer = issuer; }

    /** access 수명을 읽는다. 토큰 만료 시각과 쿠키 수명에 함께 쓰인다. */
    public Duration getAccessTtl() { return accessTtl; }
    /** mijang.jwt.access-ttl 주입. PT30M 같은 ISO-8601 기간 표기를 받는다. */
    public void setAccessTtl(Duration accessTtl) { this.accessTtl = accessTtl; }

    /** refresh 수명을 읽는다. */
    public Duration getRefreshTtl() { return refreshTtl; }
    /** mijang.jwt.refresh-ttl 주입. P14D 같은 표기를 받는다. */
    public void setRefreshTtl(Duration refreshTtl) { this.refreshTtl = refreshTtl; }

    /** access 쿠키 이름을 읽는다. 발급·삭제·조회가 같은 이름을 써야 한다. */
    public String getAccessCookie() { return accessCookie; }
    /** mijang.jwt.access-cookie 주입. */
    public void setAccessCookie(String accessCookie) { this.accessCookie = accessCookie; }

    /** refresh 쿠키 이름을 읽는다. */
    public String getRefreshCookie() { return refreshCookie; }
    /** mijang.jwt.refresh-cookie 주입. */
    public void setRefreshCookie(String refreshCookie) { this.refreshCookie = refreshCookie; }

    /** 쿠키에 Secure 를 붙일지 읽는다. 로컬 http 에서 true 면 쿠키가 아예 안 붙는다. */
    public boolean isCookieSecure() { return cookieSecure; }
    /** mijang.jwt.cookie-secure 주입. 배포 환경에서 true 로 바꾼다. */
    public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
}
