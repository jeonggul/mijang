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

/** 회원가입·로그인·토큰 갱신·비밀번호 관리 등 인증 API를 제공한다. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenCookies cookies;
    private final PasswordService passwordService;
    private final ResetRequestThrottle throttle;

    /** 닉네임의 형식·금지어·중복을 한 번에 판정해 사유 문구와 함께 돌려준다. */
    @GetMapping("/check-nickname")
    public ApiResponse<AvailabilityResponse> checkNickname(@RequestParam String nickname) {
        return ApiResponse.ok(authService.checkNickname(nickname));
    }

    /** 회원가입을 처리하고 생성된 사용자 id를 반환한다 — 로그인은 시키지 않는다. */
    @PostMapping("/signup")
    public ApiResponse<Long> signup(@Valid @RequestBody SignupForm form) {
        return ApiResponse.ok(authService.signup(form));
    }

    /** 로그인을 처리하고 토큰을 본문(API용)과 HttpOnly 쿠키(화면용) 두 경로로 내보낸다. */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginForm form,
                                                           HttpServletRequest request) {
        var tokens = authService.login(form, clientIp(request));
        return ResponseEntity.ok()
                .headers(cookies.issue(tokens.accessToken(), tokens.refreshToken(), tokens.remember()))
                .body(ApiResponse.ok(tokens.toResponse()));
    }

    /** HttpOnly 쿠키의 refresh 토큰으로 토큰을 갱신한다. */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(HttpServletRequest request) {
        var tokens = authService.refresh(cookies.readRefresh(request));
        return ResponseEntity.ok()
                .headers(cookies.issue(tokens.accessToken(), tokens.refreshToken(), tokens.remember()))
                .body(ApiResponse.ok(tokens.toResponse()));
    }

    /** 토큰 쿠키를 지워 로그아웃한다 — 이미 발급된 토큰은 수명까지 유효하다. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok()
                .headers(cookies.clear())
                .body(ApiResponse.ok(null));
    }
    /** 비밀번호 재설정 링크를 요청한다 — 가입 여부와 상관없이 같은 응답을 준다. */
    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody PasswordForgotForm form,
                                            HttpServletRequest request) {
        /* 가입 여부 확인 전에 제한을 센다 — 순서를 바꾸면 제한 응답으로 가입 여부가 새어 나간다. */
        if (!throttle.allow(form.getEmail(), request.getRemoteAddr())) {
            throw new BusinessException(ErrorCode.AUTH_TOO_MANY_REQUESTS, "email");
        }
        passwordService.requestReset(form.getEmail());
        return ApiResponse.ok(null);
    }

    /** 재설정 토큰으로 새 비밀번호를 저장한다 — 인증은 토큰이 대신한다. */
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetForm form) {
        passwordService.reset(form.getToken(), form.getPassword());
        return ApiResponse.ok(null);
    }

    /** 로그인 상태에서 비밀번호를 변경한다 — 변경 후 모든 기기의 로그인이 끊긴다. */
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@LoginUser SessionUser me,
                                                            @Valid @RequestBody PasswordChangeForm form) {
        passwordService.change(me.userId(), form.getCurrentPassword(), form.getNewPassword());
        return ResponseEntity.ok().headers(cookies.clear()).body(ApiResponse.ok(null));
    }
    /** 비밀번호를 확인해 회원 탈퇴를 처리하고 토큰 쿠키를 지운다. */
    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@LoginUser SessionUser me,
                                                           @Valid @RequestBody AccountDeleteForm form) {
        authService.withdraw(me.userId(), form.getPassword());
        return ResponseEntity.ok().headers(cookies.clear()).body(ApiResponse.ok(null));
    }

    /** X-Forwarded-For 맨 앞 값 우선으로 클라이언트 IP를 얻는다 — 위조 가능하므로 시도 제한 보조 용도로만 쓴다. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
