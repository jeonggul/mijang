package com.example.mijang.user.dto;

import com.example.mijang.user.policy.SignupPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 로그인 상태에서의 비밀번호 변경. 개발명세서(API) AUTH-05 */
@Getter
@Setter
public class PasswordChangeForm {

    /** 본인 확인용. 형식 검사를 걸지 않는다 — 예전 규칙으로 만든 값일 수 있다. */
    @NotBlank
    private String currentPassword;

    @NotBlank
    @Pattern(regexp = SignupPolicy.PASSWORD_REGEX, message = SignupPolicy.PASSWORD_GUIDE + "로 입력해주세요")
    private String newPassword;
}
