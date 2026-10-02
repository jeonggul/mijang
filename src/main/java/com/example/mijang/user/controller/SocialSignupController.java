package com.example.mijang.user.controller;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.TokenCookies;
import com.example.mijang.user.dto.SignupForm;
import com.example.mijang.user.dto.LoginResponse;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.oauth.SocialAuthHandlers;
import com.example.mijang.user.oauth.SocialAuthHandlers.Pending;
import com.example.mijang.user.oauth.SocialLoginService;
import com.example.mijang.user.policy.SignupPolicy;
import com.example.mijang.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 소셜로 처음 온 사용자의 가입을 마무리하고 소셜 계정을 이어 로그인시키는 API를 제공한다. */
@RestController
@RequestMapping("/api/auth/social")
@RequiredArgsConstructor
public class SocialSignupController {

    private final SocialLoginService socialLoginService;
    private final AuthService authService;
    private final UserMapper userMapper;
    private final TokenCookies cookies;

    /** 가입 화면이 미리 채울 세션의 소셜 신원 정보를 돌려준다. */
    @GetMapping("/pending")
    public ApiResponse<PendingResponse> pending(HttpServletRequest request) {
        Pending pending = requireSignupPending(request);
        return ApiResponse.ok(new PendingResponse(
                pending.provider(), pending.email(), pending.nickname()));
    }

    /** 가입을 확정하고 소셜 계정을 이은 뒤 그대로 로그인시킨다. */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<LoginResponse>> signup(HttpServletRequest request,
                                                             @Valid @RequestBody
                                                             SocialSignupForm form) {
        Pending pending = requireSignupPending(request);

        Long userId = socialLoginService.signupAndLink(
                toSignupForm(pending, form), pending.provider(), pending.providerUserId());

        request.getSession().removeAttribute(SocialAuthHandlers.PENDING_KEY);

        var tokens = authService.issueForSocial(userMapper.findById(userId));
        return ResponseEntity.ok()
                .headers(cookies.issue(tokens.accessToken(), tokens.refreshToken(), true))
                .body(ApiResponse.ok(tokens.toResponse()));
    }

    /** 세션 신원과 화면 입력을 합쳐 가입 폼을 만든다 — 이메일은 반드시 세션 것만 쓴다. */
    public static SignupForm toSignupForm(Pending pending, SocialSignupForm submitted) {
        var form = new SignupForm();
        form.setEmail(pending.email());
        form.setNickname(submitted.nickname());
        form.setPassword(submitted.password());
        return form;
    }

    /** 세션의 가입 보류 상태를 확인하고 없으면 인증 오류를 던진다. */
    private static Pending requireSignupPending(HttpServletRequest request) {
        Pending pending = SocialAuthHandlers.pendingOf(request);
        if (pending == null || pending.kind() != Pending.Kind.SIGNUP) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return pending;
    }

    /** 가입 화면이 미리 채울 값이다 — 제공자 쪽 식별자는 내보내지 않는다. */
    public record PendingResponse(String provider, String email, String nickname) {
    }

    /** 화면이 보내는 가입 입력이다 — 이메일과 제공자 정보는 받지 않고 세션에서 꺼낸다. */
    public record SocialSignupForm(
            @NotBlank(message = "닉네임을 입력해주세요")
            @Pattern(regexp = SignupPolicy.NICKNAME_REGEX,
                     message = "한글·영문·숫자 2~10자로 입력해주세요")
            String nickname,

            @NotBlank(message = "비밀번호를 입력해주세요")
            @Pattern(regexp = SignupPolicy.PASSWORD_REGEX,
                     message = "영문과 숫자를 모두 포함해 8~16자로 입력해주세요")
            String password) {
    }
}
