/*
 * SocialAuthHandlers — 소셜 로그인이 끝난 뒤의 갈래
 *
 * 이 파일이 하는 일
 *   제공자 인증이 끝나면 Spring 이 여기로 넘긴다. 우리는 세 갈래로 보낸다.
 *
 *     연결돼 있다     → JWT 쿠키를 굽고 대시보드(관리자는 관리자 화면)로
 *     연결이 필요하다 → 비밀번호 확인 화면으로
 *     실패했다        → 로그인 화면으로, 이유를 붙여서
 *
 *   왜 여기서 JWT 를 굽는가
 *     이 서비스의 인증은 JWT 쿠키 하나로 끝난다. 소셜만 세션을 쓰면 인증 방식이 두 벌이
 *     되어, 토큰 무효화·강제 로그아웃 같은 규칙을 두 곳에 만들어야 한다. 들어오는 문만
 *     다르고 그 뒤는 같은 길을 걷게 한다.
 */
package com.example.mijang.user.oauth;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.security.TokenCookies;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** 소셜 로그인 성공·실패 처리. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialAuthHandlers {

    /** 연결 대기 정보를 담아 둘 세션 키. 비밀번호 확인 화면이 꺼내 쓴다. */
    public static final String PENDING_KEY = "mijang.social.pending";

    private final SocialLoginService socialLoginService;
    private final AuthService authService;
    private final TokenCookies cookies;

    /** 연결을 기다리는 소셜 신원. 비밀번호를 맞히면 이 값으로 잇는다. */
    public record Pending(String provider, String providerUserId, String email) {
    }

    public AuthenticationSuccessHandler success() {
        return (request, response, authentication) -> {
            SocialProfile profile = profileOf(authentication);
            try {
                SocialLoginService.Result result = socialLoginService.resolve(profile);
                if (result.needsLink()) {
                    holdForLink(request, profile);
                    redirect(response, "/social-link");
                    return;
                }
                issueCookies(response, result.user());
                redirect(response, "ADMIN".equals(result.user().role()) ? "/admin" : "/dashboard");
            } catch (BusinessException e) {
                /* 사용자에게는 로그인 화면에서 이유를 보여 준다. 코드만 넘기고
                   문구는 화면이 고른다 — 주소창에 긴 한글이 실리지 않게 */
                redirect(response, "/login?social=" + e.errorCode().code());
            }
        };
    }

    public AuthenticationFailureHandler failure() {
        return (HttpServletRequest request, HttpServletResponse response,
                AuthenticationException exception) -> {
            /* 사용자가 동의 화면에서 취소한 경우가 대부분이라 경고로 남기지 않는다 */
            log.info("[소셜] 인증 실패 — {}", exception.getMessage());
            redirect(response, "/login?social=FAILED");
        };
    }

    /** 확인이 끝난 뒤 잇는다. 세션에 담아 둔 신원을 쓰고 바로 비운다. */
    public void completeLink(HttpServletRequest request, Long userId) {
        Pending pending = pendingOf(request);
        if (pending == null) {
            return;
        }
        socialLoginService.link(userId, pending.provider(), pending.providerUserId());
        request.getSession().removeAttribute(PENDING_KEY);
    }

    public static Pending pendingOf(HttpServletRequest request) {
        var session = request.getSession(false);
        return session == null ? null : (Pending) session.getAttribute(PENDING_KEY);
    }

    private void holdForLink(HttpServletRequest request, SocialProfile profile) {
        request.getSession(true).setAttribute(PENDING_KEY,
                new Pending(profile.provider(), profile.providerUserId(), profile.email()));
    }

    private void issueCookies(HttpServletResponse response, User user) {
        var tokens = authService.issueForSocial(user);
        HttpHeaders headers = cookies.issue(
                tokens.accessToken(), tokens.refreshToken(), tokens.remember());
        headers.forEach((name, values) -> values.forEach(v -> response.addHeader(name, v)));
    }

    private static SocialProfile profileOf(Authentication authentication) {
        var token = (org.springframework.security.oauth2.client.authentication
                .OAuth2AuthenticationToken) authentication;
        OAuth2User principal = token.getPrincipal();
        return SocialProfile.of(token.getAuthorizedClientRegistrationId(), principal.getAttributes());
    }

    /* 우리 화면 경로만 넘긴다. 제공자가 준 값을 여기 붙이지 않는다 —
       붙이면 열린 리디렉션이 된다 */
    private static void redirect(HttpServletResponse response, String path) throws IOException {
        response.sendRedirect(path);
    }
}
