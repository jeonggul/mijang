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

/** 요청마다 헤더·쿠키에서 access token 을 찾아 인증을 세운다. */
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

    /** 토큰이 있으면 검증해 인증을 세우고, 없거나 깨졌으면 막지 않고 넘긴다. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = resolve(request);
            boolean authenticated = false;

            if (token != null) {
                try {
                    Claims claims = jwtProvider.parse(token);
                    // 갱신용 토큰과 비밀번호 변경 전에 나간 토큰은 통과시키지 않는다.
                    if (jwtProvider.isType(claims, JwtProvider.TYPE_ACCESS)
                            && !versions.isStale(jwtProvider.userId(claims),
                                                 jwtProvider.passwordVersion(claims))) {
                        authenticate(request, claims);
                        authenticated = true;
                    }
                } catch (JwtException | IllegalArgumentException e) {
                    // 만료·위조 토큰은 비로그인으로 취급한다.
                    SecurityContextHolder.clearContext();
                }
            }
            if (!authenticated) {
                slideSession(request, response);
            }
        }
        chain.doFilter(request, response);
    }

    /** access 가 없거나 만료됐을 때 refresh 쿠키로 조용히 재발급한다. */
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
            // refresh 도 못 쓰면 비로그인으로 둔다.
            SecurityContextHolder.clearContext();
        }
    }

    /** 클레임으로 SecurityContext 에 인증을 세운다. */
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

    /** 헤더 먼저, 쿠키 나중으로 토큰을 찾는다. 없으면 null 이다. */
    private String resolve(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length()).trim();
        }
        return cookies.readAccess(request);
    }
}
