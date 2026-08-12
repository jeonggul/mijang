package com.example.mijang.security;

import com.example.mijang.config.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 쿠키 발급·삭제.
 *
 * <p>둘 다 HttpOnly 다. 화면(Thymeleaf)은 이 쿠키로 인증되고, JS 는 토큰을 읽을 수 없다.
 * SameSite=Lax 로 두어 폼 전송·링크 이동에서는 붙고 외부 사이트의 요청에는 붙지 않게 한다.
 */
@Component
public class TokenCookies {

    private final JwtProperties props;

    public TokenCookies(JwtProperties props) {
        this.props = props;
    }

    /**
     * 로그인 성공 시 두 쿠키를 함께 굽는다.
     *
     * <p>Set-Cookie 는 값마다 헤더가 하나씩 필요해서 add 를 두 번 부른다.
     * set 을 쓰면 뒤엣것이 앞엣것을 덮어써 쿠키가 하나만 나간다.
     */
    public HttpHeaders issue(String accessToken, String refreshToken, boolean remember) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                build(props.getAccessCookie(), accessToken, props.getAccessTtl()).toString());
        // 로그인 상태 유지를 끄면 세션 쿠키(수명 미지정)로 굽는다. 브라우저를 닫으면 사라진다.
        headers.add(HttpHeaders.SET_COOKIE,
                remember
                        ? build(props.getRefreshCookie(), refreshToken, props.getRefreshTtl()).toString()
                        : session(props.getRefreshCookie(), refreshToken).toString());
        return headers;
    }

    /** 수명을 지정하지 않은 쿠키. 브라우저가 닫히면 지워진다. */
    private ResponseCookie session(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true).secure(props.isCookieSecure())
                .path("/").sameSite("Lax").build();
    }

    /**
     * access 만 다시 굽는다. refresh 를 그대로 두고 access 만 연장하고 싶을 때 쓴다.
     *
     * <p>지금 갱신 흐름은 두 개를 모두 새로 발급하므로 쓰이지 않지만,
     * refresh 회전을 도입하지 않기로 한 결정(2.2)이 바뀌면 이 메서드가 필요해진다.
     */
    public HttpHeaders issueAccessOnly(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                build(props.getAccessCookie(), accessToken, props.getAccessTtl()).toString());
        return headers;
    }

    /**
     * 로그아웃. 같은 이름·같은 경로로 빈 값에 수명 0 인 쿠키를 덮어써 지운다.
     *
     * <p>HttpOnly 라 JS 가 지울 수 없다. 서버만 할 수 있는 일이다.
     * path 가 다르면 브라우저가 다른 쿠키로 보고 지우지 않으므로 build() 로 조건을 맞춘다.
     */
    public HttpHeaders clear() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                build(props.getAccessCookie(), "", Duration.ZERO).toString());
        headers.add(HttpHeaders.SET_COOKIE,
                build(props.getRefreshCookie(), "", Duration.ZERO).toString());
        return headers;
    }

    /** 요청에서 access 쿠키를 꺼낸다. 없으면 null. 필터가 쓴다. */
    public String readAccess(HttpServletRequest request) {
        return read(request, props.getAccessCookie());
    }

    /** 요청에서 refresh 쿠키를 꺼낸다. 없으면 null. 갱신 API 가 쓴다. */
    public String readRefresh(HttpServletRequest request) {
        return read(request, props.getRefreshCookie());
    }

    /**
     * 쿠키 한 장을 만든다. 발급과 삭제가 같은 속성을 쓰도록 여기 한 곳에 모았다.
     *
     * <p>SameSite=Lax — 링크 이동과 폼 전송에는 붙고, 외부 사이트가 스크립트로 보내는
     * 요청에는 붙지 않는다. CSRF 를 끈 것에 대한 실질적 방어다(5.8 참고).
     */
    private ResponseCookie build(String name, String value, Duration ttl) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.isCookieSecure())
                .path("/")
                .maxAge(ttl)
                .sameSite("Lax")
                .build();
    }

    /**
     * 이름이 같은 쿠키를 찾는다.
     *
     * <p>빈 값은 없는 것으로 본다. 로그아웃으로 지운 쿠키가 브라우저에 따라
     * 값이 빈 채로 남아 오는 경우가 있어서다.
     */
    private String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var c : request.getCookies()) {
            if (name.equals(c.getName()) && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
