package com.example.mijang.user.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.TokenCookies;
import com.example.mijang.user.dto.AvailabilityResponse;
import com.example.mijang.user.dto.LoginForm;
import com.example.mijang.user.dto.LoginResponse;
import com.example.mijang.user.dto.SignupForm;
import com.example.mijang.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. 개발명세서(API) AUTH-001~003 · 화면 SR-002
 *
 * <p>토큰은 두 경로로 나간다. 본문의 accessToken 은 API 클라이언트용이고,
 * HttpOnly 쿠키는 Thymeleaf 화면용이다. 자세한 이유는 미장-로그인-구현 2.1.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenCookies cookies;

    /**
     * 닉네임 사용 가능 확인. 가입 폼의 중복 확인 버튼이 부른다.
     *
     * <p>형식·금지어·중복을 한 번에 판정해 사유 문구까지 돌려준다.
     * 화면이 규칙을 다시 해석하지 않아도 되도록 문구를 서버가 만든다.
     */
    @GetMapping("/check-nickname")
    public ApiResponse<AvailabilityResponse> checkNickname(@RequestParam String nickname) {
        return ApiResponse.ok(authService.checkNickname(nickname));
    }

    /**
     * AUTH-001 회원가입. 가입만 하고 로그인은 시키지 않는다.
     *
     * <p>쿠키를 굽지 않으므로 반환형이 ResponseEntity 가 아니라 ApiResponse 다.
     *
     * @return 생성된 사용자 id
     */
    @PostMapping("/signup")
    public ApiResponse<Long> signup(@Valid @RequestBody SignupForm form) {
        return ApiResponse.ok(authService.signup(form));
    }

    /**
     * AUTH-002 로그인.
     *
     * <p>토큰을 두 경로로 내보낸다 — 본문의 accessToken 은 API 클라이언트가,
     * Set-Cookie 는 브라우저 화면이 쓴다. 쿠키를 실어야 해서 ResponseEntity 로 받는다.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginForm form) {
        var tokens = authService.login(form);
        return ResponseEntity.ok()
                .headers(cookies.issue(tokens.accessToken(), tokens.refreshToken(), tokens.remember()))
                .body(ApiResponse.ok(tokens.toResponse()));
    }

    /**
     * AUTH-03 토큰 갱신.
     *
     * <p>요청 본문이 없다. refresh 는 HttpOnly 쿠키에만 있어 클라이언트가 보낼 수 없고,
     * 서버가 요청에서 직접 꺼낸다. 그래서 파라미터가 HttpServletRequest 다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(HttpServletRequest request) {
        var tokens = authService.refresh(cookies.readRefresh(request));
        return ResponseEntity.ok()
                .headers(cookies.issue(tokens.accessToken(), tokens.refreshToken(), tokens.remember()))
                .body(ApiResponse.ok(tokens.toResponse()));
    }

    /**
     * AUTH-003 로그아웃.
     *
     * <p>refresh 를 서버에 저장하지 않으므로 쿠키를 지우는 것이 전부다.
     * 이미 발급된 토큰은 수명이 다할 때까지 유효하다 (미장-로그인-구현 2.2).
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok()
                .headers(cookies.clear())
                .body(ApiResponse.ok(null));
    }
}
