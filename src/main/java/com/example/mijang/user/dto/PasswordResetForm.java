package com.example.mijang.user.dto;

import com.example.mijang.user.policy.SignupPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 새 비밀번호 저장. 개발명세서(API) AUTH-05 */
@Getter
@Setter
public class PasswordResetForm {

    /** 메일 링크에 실려 온 값. 화면이 주소에서 읽어 그대로 보낸다. */
    @NotBlank
    private String token;

    /** 가입 때와 같은 규칙을 쓴다. 재설정만 느슨하면 규칙을 둔 의미가 없다. */
    @NotBlank
    @Pattern(regexp = SignupPolicy.PASSWORD_REGEX, message = SignupPolicy.PASSWORD_GUIDE + "로 입력해주세요")
    private String password;
}
