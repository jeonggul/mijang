/*
 * SocialSignupController — 소셜로 처음 온 사람의 가입을 마무리한다
 *
 * 이 파일이 하는 일
 *   제공자에게서 받은 이메일·닉네임을 화면에 채워 주고, 사용자가 비밀번호와
 *   닉네임을 확정하면 계정을 만들어 소셜 계정을 잇고 그대로 로그인시킨다.
 *
 *   왜 비밀번호를 받는가
 *     받지 않으면 로그인 수단이 소셜 하나뿐인 계정이 된다. 그 상태에서 연동을
 *     끊으면 들어올 문이 사라지고, 비밀번호 찾기로도 복구되지 않는다.
 *
 *   신원은 요청 본문이 아니라 세션에서 꺼낸다. 본문으로 받으면 아무 이메일이나
 *   적어 남의 주소로 계정을 만들거나, 남의 소셜 계정을 자기 것으로 붙일 수 있다.
 */
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

/** 소셜 첫 가입. {@code AUTH-07} */
@RestController
@RequestMapping("/api/auth/social")
@RequiredArgsConstructor
public class SocialSignupController {

    private final SocialLoginService socialLoginService;
    private final AuthService authService;
    private final UserMapper userMapper;
    private final TokenCookies cookies;

    /** 가입 화면이 미리 채울 값. 비밀번호를 받기 전이라 아직 계정은 없다. */
    @GetMapping("/pending")
    public ApiResponse<PendingResponse> pending(HttpServletRequest request) {
        Pending pending = requireSignupPending(request);
        return ApiResponse.ok(new PendingResponse(
                pending.provider(), pending.email(), pending.nickname()));
    }

    /**
     * 가입을 확정하고 소셜을 이은 뒤 그대로 로그인시킨다.
     *
     * <p>방금 만든 비밀번호를 다시 치게 하지 않는다. {@code SocialLinkController} 가
     * 같은 판단을 하고 있다.
     */
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

    /**
     * 세션 신원과 화면 입력을 합쳐 가입 폼을 만든다.
     *
     * <p>이메일은 <b>세션 것만</b> 쓴다. 화면이 보낸 값을 쓰면 아무 주소로나
     * 계정을 만들 수 있다. 테스트가 이 규칙을 고정한다.
     */
    public static SignupForm toSignupForm(Pending pending, SocialSignupForm submitted) {
        var form = new SignupForm();
        form.setEmail(pending.email());
        form.setNickname(submitted.nickname());
        form.setPassword(submitted.password());
        return form;
    }

    /** 가입 보류 상태가 아니면 진행할 수 없다. 세션이 끊겼거나 직접 부른 경우다. */
    private static Pending requireSignupPending(HttpServletRequest request) {
        Pending pending = SocialAuthHandlers.pendingOf(request);
        if (pending == null || pending.kind() != Pending.Kind.SIGNUP) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return pending;
    }

    /** 화면이 채워 넣을 값. 제공자 쪽 식별자는 내보내지 않는다 — 화면이 쓸 일이 없다. */
    public record PendingResponse(String provider, String email, String nickname) {
    }

    /**
     * 화면이 보내는 값. <b>이메일과 제공자 정보는 받지 않는다</b> — 세션에서 꺼낸다.
     *
     * <p>형식은 여기서 걸러 400 으로 돌려주고, 금지어·중복·추측 가능성은
     * {@code AuthService.signup()} 이 본다.
     */
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
