package com.example.mijang.security;

import com.example.mijang.config.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 인증 쿠키를 발급·삭제한다. 두 쿠키 모두 HttpOnly · SameSite=Lax 를 유지해야 한다. */
@Component
public class TokenCookies {

    private final JwtProperties props;

    public TokenCookies(JwtProperties props) {
        this.props = props;
    }

    /** 로그인 성공 시 access·refresh 쿠키를 함께 굽는다. */
    public HttpHeaders issue(String accessToken, String refreshToken, boolean remember) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                build(props.getAccessCookie(), accessToken, props.getAccessTtl()).toString());
        // 로그인 상태 유지를 끄면 브라우저 종료 시 사라지는 세션 쿠키로 굽는다.
        headers.add(HttpHeaders.SET_COOKIE,
                remember
                        ? build(props.getRefreshCookie(), refreshToken, props.getRefreshTtl()).toString()
                        : session(props.getRefreshCookie(), refreshToken).toString());
        return headers;
    }

    /** 수명 미지정 세션 쿠키를 만든다. */
    private ResponseCookie session(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true).secure(props.isCookieSecure())
                .path("/").sameSite("Lax").build();
    }

    /** access 쿠키만 다시 굽는다. */
    public HttpHeaders issueAccessOnly(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                build(props.getAccessCookie(), accessToken, props.getAccessTtl()).toString());
        return headers;
    }

    /** 두 쿠키를 빈 값·수명 0 으로 덮어써 지운다. HttpOnly 라 서버만 지울 수 있다. */
    public HttpHeaders clear() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                build(props.getAccessCookie(), "", Duration.ZERO).toString());
        headers.add(HttpHeaders.SET_COOKIE,
                build(props.getRefreshCookie(), "", Duration.ZERO).toString());
        return headers;
    }

    /** 요청에서 access 쿠키를 꺼낸다. 없으면 null 이다. */
    public String readAccess(HttpServletRequest request) {
        return read(request, props.getAccessCookie());
    }

    /** 요청에서 refresh 쿠키를 꺼낸다. 없으면 null 이다. */
    public String readRefresh(HttpServletRequest request) {
        return read(request, props.getRefreshCookie());
    }

    /** 공통 속성으로 쿠키 한 장을 만든다. SameSite=Lax 가 CSRF 를 끈 것에 대한 방어다(5.8). */
    private ResponseCookie build(String name, String value, Duration ttl) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.isCookieSecure())
                .path("/")
                .maxAge(ttl)
                .sameSite("Lax")
                .build();
    }

    /** 이름이 같은 쿠키를 찾는다. 빈 값은 없는 것으로 본다. */
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
