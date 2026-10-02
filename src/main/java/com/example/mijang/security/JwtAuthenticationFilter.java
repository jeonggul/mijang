package com.example.mijang.security;

import com.example.mijang.user.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 access token 을 찾아 인증을 세운다.
 *
 * <p>토큰을 두 군데에서 찾는다.
 * <ul>
 *   <li>{@code Authorization: Bearer ...} — API 클라이언트 (미장-API명세서 1.3)</li>
 *   <li>HttpOnly 쿠키 — Thymeleaf 화면. 브라우저가 헤더를 붙일 수 없어서 필요하다</li>
 * </ul>
 *
 * <p>토큰이 없거나 깨졌으면 인증을 세우지 않고 그냥 통과시킨다.
 * 막는 것은 SecurityConfig 의 몫이고, 이 필터는 "누구인지"만 판정한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtProvider jwtProvider;
    private final TokenCookies cookies;
    private final AuthService authService;
    private final PasswordVersionRegistry versions;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, TokenCookies cookies,
                                   AuthService authService, PasswordVersionRegistry versions) {
        this.jwtProvider = jwtProvider;
        this.cookies = cookies;
        this.authService = authService;
        this.versions = versions;
    }

    /**
     * 요청 하나를 처리한다. 토큰이 있으면 검증해 인증을 세우고, 없거나 깨졌으면 그냥 넘긴다.
     *
     * <p>여기서 401 을 던지지 않는 것이 요점이다. 이 필터는 모든 요청을 지나가므로
     * 여기서 막으면 랜딩·로그인 화면까지 못 열린다. 막는 판단은 SecurityConfig 가 한다.
     *
     * <p>이미 인증이 서 있으면 건드리지 않는다. 다른 필터가 세운 것을 덮어쓰지 않기 위해서다.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = resolve(request);
            boolean authenticated = false;

            if (token != null) {
                try {
                    Claims claims = jwtProvider.parse(token);
                    // 갱신용 토큰으로 일반 요청을 통과시키지 않는다.
                    // 비밀번호를 바꾸기 전에 나간 토큰도 여기서 끊는다 — 표는 메모리에
                    // 있고 바꾼 계정만 들어 있어 매 요청 DB 를 보지 않는다(8.1.7).
                    // 막히면 아래 slideSession 으로 내려가는데, refresh 도 세대가
                    // 어긋나 거기서 함께 막힌다.
                    if (jwtProvider.isType(claims, JwtProvider.TYPE_ACCESS)
                            && !versions.isStale(jwtProvider.userId(claims),
                                                 jwtProvider.passwordVersion(claims))) {
                        authenticate(request, claims);
                        authenticated = true;
                    }
                } catch (JwtException | IllegalArgumentException e) {
                    // 만료·위조는 비로그인으로 취급한다. 여기서 예외를 던지면 공개 화면까지 막힌다
                    SecurityContextHolder.clearContext();
                }
            }
            if (!authenticated) {
                slideSession(request, response);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 클레임으로 SecurityContext 에 인증을 세운다.
     *
     * <p>권한 문자열에 {@code ROLE_} 를 붙인다. 스프링 시큐리티의 {@code hasRole("ADMIN")} 은
     * 내부적으로 {@code ROLE_ADMIN} 을 찾기 때문이다. DB 에는 접두사 없이 저장돼 있다.
     *
     * <p>비밀번호 자리에는 null 을 넣는다. 토큰으로 이미 검증이 끝났고,
     * 자격 증명을 메모리에 남길 이유가 없다.
     */
    /**
     * access 가 없거나 만료됐을 때 refresh 쿠키로 조용히 다시 발급한다.
     *
     * <p>화면(Thymeleaf)은 스스로 갱신 요청을 보낼 수 없다. 링크를 누르면 그냥 이동할 뿐이라
     * 갱신을 끼워 넣을 자리가 없다. 이 처리가 없으면 refresh 수명이 14일이어도
     * access 가 끊기는 30분마다 로그인 화면으로 튕긴다.
     *
     * <p>사용자 상태는 {@link AuthService#refresh} 가 DB 에서 다시 읽으므로
     * 정지·탈퇴 계정이 갱신으로 되살아나지 않는다.
     */
    private void slideSession(HttpServletRequest request, HttpServletResponse response) {
        String refresh = cookies.readRefresh(request);
        if (refresh == null) {
            return;
        }
        try {
            var tokens = authService.refresh(refresh);
            cookies.issue(tokens.accessToken(), tokens.refreshToken(), tokens.remember())
                    .forEach((name, values) -> values.forEach(v -> response.addHeader(name, v)));

            authenticate(request, jwtProvider.parse(tokens.accessToken()));
        } catch (RuntimeException e) {
            // refresh 도 못 쓰면 비로그인이다. 막는 판단은 SecurityConfig 의 몫이다
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(HttpServletRequest request, Claims claims) {
        SessionUser user = new SessionUser(
                jwtProvider.userId(claims),
                jwtProvider.nickname(claims),
                jwtProvider.role(claims));

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 토큰을 찾는다. 헤더가 먼저고 쿠키가 나중이다.
     *
     * <p>API 클라이언트가 헤더를 명시적으로 보냈다면 그 의도를 따르는 것이 맞다.
     * 브라우저 화면은 헤더를 붙일 수 없으므로 쿠키로 넘어간다(2.1).
     *
     * @return 토큰 문자열. 어디에도 없으면 null
     */
    private String resolve(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length()).trim();
        }
        return cookies.readAccess(request);
    }
}
