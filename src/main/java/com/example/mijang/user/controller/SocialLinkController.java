package com.example.mijang.user.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.security.TokenCookies;
import com.example.mijang.user.dto.LoginForm;
import com.example.mijang.user.dto.LoginResponse;
import com.example.mijang.user.oauth.SocialAuthHandlers;
import com.example.mijang.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이미 가입된 계정에 비밀번호 확인을 거쳐 소셜 계정을 잇는 API를 제공한다. */
@RestController
@RequestMapping("/api/auth/social")
@RequiredArgsConstructor
public class SocialLinkController {

    private final AuthService authService;
    private final SocialAuthHandlers socialAuthHandlers;
    private final TokenCookies cookies;

    /** 비밀번호를 확인해 소셜 계정을 연결하고 그대로 로그인시킨다. */
    @PostMapping("/link")
    public ResponseEntity<ApiResponse<LoginResponse>> link(HttpServletRequest request,
                                                           @Valid @RequestBody LinkForm form) {
        var pending = SocialAuthHandlers.pendingOf(request);
        /* LINK 대기 세션만 받는다 — SIGNUP 세션이 이 경로로 들어오면 안 된다 */
        if (pending == null || pending.kind() != SocialAuthHandlers.Pending.Kind.LINK) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }

        /* 이메일은 본문이 아니라 세션 것을 쓴다 — 본문으로 받으면 시도 제한이 우회된다 */
        LoginForm login = new LoginForm();
        login.setEmail(pending.email());
        login.setPassword(form.password());
        login.setRememberMe(true);

        AuthService.Tokens tokens = authService.login(login, clientIp(request));
        socialAuthHandlers.completeLink(request, tokens.user().id());

        return ResponseEntity.ok()
                .headers(cookies.issue(tokens.accessToken(), tokens.refreshToken(), true))
                .body(ApiResponse.ok(tokens.toResponse()));
    }

    /** 프록시 뒤에서도 원래 클라이언트 IP를 얻는다 — 로그인 시도 제한이 이 값을 센다. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record LinkForm(@NotBlank String password) {
    }
}
