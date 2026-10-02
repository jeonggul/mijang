package com.example.mijang.user.dto;

import com.example.mijang.user.policy.SignupPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 재설정 토큰으로 새 비밀번호를 저장하는 입력이다. */
@Getter
@Setter
public class PasswordResetForm {

    /** 메일 링크에 실려 온 재설정 토큰이다. */
    @NotBlank
    private String token;

    /** 새 비밀번호다 — 가입 때와 같은 규칙을 쓴다. */
    @NotBlank
    @Pattern(regexp = SignupPolicy.PASSWORD_REGEX, message = SignupPolicy.PASSWORD_GUIDE + "로 입력해주세요")
    private String password;
}
