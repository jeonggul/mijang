package com.example.mijang.user.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.user.dto.LoginForm;
import com.example.mijang.user.dto.SignupForm;
import com.example.mijang.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. 개발명세서(API) AUTH-001~003 · 화면 SR-001
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** AUTH-001 회원가입 */
    @PostMapping("/signup")
    public ApiResponse<Long> signup(@Valid @RequestBody SignupForm form) {
        return ApiResponse.ok(authService.signup(form));
    }

    /** AUTH-002 로그인 */
    @PostMapping("/login")
    public ApiResponse<Long> login(@Valid @RequestBody LoginForm form) {
        return ApiResponse.ok(authService.login(form));
    }

    /** AUTH-003 로그아웃 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok(null);
    }
}
