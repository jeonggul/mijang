package com.example.mijang.user.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import com.example.mijang.user.dto.PasswordChangeForm;
import com.example.mijang.user.dto.PasswordForgotForm;
import com.example.mijang.user.dto.PasswordResetForm;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.user.policy.ResetRequestThrottle;
import com.example.mijang.user.service.PasswordService;
import org.springframework.web.bind.annotation.PatchMapping;
import com.example.mijang.user.dto.AccountDeleteForm;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * HttpOnly 쿠키는 Thymeleaf 화면용이다. 자세한 이유는 미장-auth-구현 2.1.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenCookies cookies;
    private final PasswordService passwordService;
    private final ResetRequestThrottle throttle;

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
     * 이미 발급된 토큰은 수명이 다할 때까지 유효하다 (미장-auth-구현 2.2).
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok()
                .headers(cookies.clear())
                .body(ApiResponse.ok(null));
    }
    /**
     * AUTH-05 재설정 링크 요청.
     *
     * <p>가입 여부와 상관없이 같은 응답을 준다(8.1.3). 화면도 같은 문구를 띄운다.
     */
    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody PasswordForgotForm form,
                                            HttpServletRequest request) {
        /* 가입 여부를 보기 전에 센다. 가입된 주소만 제한하면 "제한에 걸렸다"가 곧
           가입돼 있다는 뜻이 되어 위에서 막아 둔 것이 도로 새어 나간다. */
        if (!throttle.allow(form.getEmail(), request.getRemoteAddr())) {
            throw new BusinessException(ErrorCode.AUTH_TOO_MANY_REQUESTS, "email");
        }
        passwordService.requestReset(form.getEmail());
        return ApiResponse.ok(null);
    }

    /** AUTH-05 링크로 들어온 사용자의 새 비밀번호 저장. 인증이 필요 없고 토큰이 대신한다. */
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetForm form) {
        passwordService.reset(form.getToken(), form.getPassword());
        return ApiResponse.ok(null);
    }

    /**
     * AUTH-05 로그인 상태에서의 비밀번호 변경.
     *
     * <p>바꾸고 나면 <b>모든 기기의 로그인이 끊긴다.</b> 다른 기기는 비밀번호 세대가
     * 어긋나 갱신에서 막히고, 이 브라우저는 여기서 쿠키를 지워 함께 내보낸다.
     * 지금 쓰던 창만 남겨 두면 "이 창은 왜 살아 있나"가 되어 규칙이 흐려진다.
     */
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@LoginUser SessionUser me,
                                                            @Valid @RequestBody PasswordChangeForm form) {
        passwordService.change(me.userId(), form.getCurrentPassword(), form.getNewPassword());
        return ResponseEntity.ok().headers(cookies.clear()).body(ApiResponse.ok(null));
    }
    /**
     * AUTH-06 회원 탈퇴.
     *
     * <p>성공하면 쿠키를 지워 보낸다. 남겨 두면 access 가 만료되는 30분 동안
     * 탈퇴한 계정으로 화면이 열린다.
     */
    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@LoginUser SessionUser me,
                                                           @Valid @RequestBody AccountDeleteForm form) {
        authService.withdraw(me.userId(), form.getPassword());
        return ResponseEntity.ok().headers(cookies.clear()).body(ApiResponse.ok(null));
    }
}
