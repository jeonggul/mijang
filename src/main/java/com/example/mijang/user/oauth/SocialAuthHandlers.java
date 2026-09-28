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

/** 소셜 인증이 끝난 뒤 로그인·연결 확인·가입·실패 네 갈래로 보내는 핸들러를 만든다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialAuthHandlers {

    /** 연결 대기 정보를 담아 둘 세션 키다. */
    public static final String PENDING_KEY = "mijang.social.pending";

    private final SocialLoginService socialLoginService;
    private final AuthService authService;
    private final TokenCookies cookies;

    /** 중간 화면을 거치는 동안 세션에만 들고 있는 소셜 신원이다 — kind는 LINK·SIGNUP 갈래, nickname은 LINK에서 null이다. */
    public record Pending(Kind kind, String provider, String providerUserId,
                          String email, String nickname) {

        public enum Kind { LINK, SIGNUP }
    }

    /** 소셜 인증 성공 시 갈래를 나눠 리디렉션하는 핸들러를 만든다. */
    public AuthenticationSuccessHandler success() {
        return (request, response, authentication) -> {
            SocialProfile profile = profileOf(authentication);
            try {
                SocialLoginService.Result result = socialLoginService.resolve(profile);
                switch (result.kind()) {
                    case NEEDS_LINK -> {
                        hold(request, Pending.Kind.LINK, profile, null);
                        redirect(response, "/social-link");
                        return;
                    }
                    case NEEDS_SIGNUP -> {
                        hold(request, Pending.Kind.SIGNUP, profile, result.nickname());
                        redirect(response, "/social-signup");
                        return;
                    }
                    case LOGGED_IN -> { /* 아래로 흘러간다 */ }
                }
                issueCookies(response, result.user());
                redirect(response, "ADMIN".equals(result.user().role()) ? "/admin" : "/dashboard");
            } catch (BusinessException e) {
                /* 오류 코드만 넘기고 문구는 화면이 고른다 */
                redirect(response, "/login?social=" + e.errorCode().code());
            }
        };
    }

    /** 소셜 인증 실패 시 로그인 화면으로 돌려보내는 핸들러를 만든다. */
    public AuthenticationFailureHandler failure() {
        return (HttpServletRequest request, HttpServletResponse response,
                AuthenticationException exception) -> {
            /* 사용자가 동의 화면에서 취소한 경우가 대부분이라 경고로 남기지 않는다 */
            log.info("[소셜] 인증 실패 — {}", exception.getMessage());
            redirect(response, "/login?social=FAILED");
        };
    }

    /** 확인이 끝난 뒤 세션의 신원으로 연동을 잇고 세션을 비운다. */
    public void completeLink(HttpServletRequest request, Long userId) {
        Pending pending = pendingOf(request);
        if (pending == null) {
            return;
        }
        socialLoginService.link(userId, pending.provider(), pending.providerUserId());
        request.getSession().removeAttribute(PENDING_KEY);
    }

    /** 세션에 담긴 대기 신원을 꺼낸다 — 없으면 null이다. */
    public static Pending pendingOf(HttpServletRequest request) {
        var session = request.getSession(false);
        return session == null ? null : (Pending) session.getAttribute(PENDING_KEY);
    }

    /** 갈래와 신원을 세션에 담는다. */
    private void hold(HttpServletRequest request, Pending.Kind kind,
                      SocialProfile profile, String nickname) {
        request.getSession(true).setAttribute(PENDING_KEY, new Pending(
                kind, profile.provider(), profile.providerUserId(), profile.email(), nickname));
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

    /* 우리 화면 경로만 넘긴다 — 제공자가 준 값을 붙이면 열린 리디렉션이 된다 */
    private static void redirect(HttpServletResponse response, String path) throws IOException {
        response.sendRedirect(path);
    }
}
