/*
 * SocialLinkController — 이미 가입된 계정에 소셜을 잇는다
 *
 * 이 파일이 하는 일
 *   같은 이메일로 이미 가입된 사람이 소셜로 들어왔을 때, 기존 비밀번호를 한 번 받아
 *   확인하고 두 계정을 잇는다.
 *
 *   왜 비밀번호를 받는가
 *     제공자가 이메일을 검증하지 않으면 남의 주소를 적은 소셜 계정으로 그 사람의
 *     매매 원장에 들어갈 수 있다. 자동으로 이으면 그 길이 열린다.
 *
 *   연결할 신원은 요청 본문이 아니라 <b>세션</b>에서 꺼낸다. 본문으로 받으면
 *   아무 provider_user_id 나 적어 남의 소셜 계정을 자기 계정에 붙일 수 있다.
 */
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

/** 소셜 계정 연결. {@code AUTH-07} */
@RestController
@RequestMapping("/api/auth/social")
@RequiredArgsConstructor
public class SocialLinkController {

    private final AuthService authService;
    private final SocialAuthHandlers socialAuthHandlers;
    private final TokenCookies cookies;

    /**
     * 비밀번호를 확인하고 연결한 뒤 그대로 로그인시킨다.
     *
     * <p>확인이 곧 로그인이라 토큰까지 여기서 발급한다. 확인만 하고 다시 로그인하게 하면
     * 같은 비밀번호를 두 번 치게 된다.
     */
    @PostMapping("/link")
    public ResponseEntity<ApiResponse<LoginResponse>> link(HttpServletRequest request,
                                                           @Valid @RequestBody LinkForm form) {
        var pending = SocialAuthHandlers.pendingOf(request);
        if (pending == null) {
            /* 세션이 끊겼거나 곧바로 이 API 를 부른 경우다. 다시 소셜 로그인부터 해야 한다 */
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }

        /* 이메일도 세션 것을 쓴다. 본문으로 받으면 남의 계정 비밀번호를 맞혀 보는
           자리가 되고, 로그인 시도 제한도 우회된다 */
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

    /** 프록시 뒤에서도 원래 주소를 본다. 로그인 시도 제한이 이 값을 센다. */
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
