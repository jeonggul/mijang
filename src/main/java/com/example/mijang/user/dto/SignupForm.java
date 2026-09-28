package com.example.mijang.user.dto;

import com.example.mijang.user.policy.SignupPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 회원가입 입력이다 — 형식 규칙은 {@link SignupPolicy}의 상수를 참조만 한다. */
@Getter
@Setter
public class SignupForm {

    @NotBlank(message = "이메일을 입력해주세요")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    /* 탈퇴 시 "{id}.withdrawn." 접두를 붙여도 VARCHAR(255)를 넘지 않게 가입 단계에서 길이를 막는다 */
    @Size(max = SignupPolicy.EMAIL_MAX_LENGTH, message = "이메일이 너무 깁니다")
    private String email;

    @NotBlank(message = "비밀번호를 입력해주세요")
    @Pattern(regexp = SignupPolicy.PASSWORD_REGEX,
             message = "영문과 숫자를 모두 포함해 8~16자로 입력해주세요")
    private String password;

    @NotBlank(message = "닉네임을 입력해주세요")
    @Pattern(regexp = SignupPolicy.NICKNAME_REGEX,
             message = "한글·영문·숫자 2~10자로 입력해주세요")
    private String nickname;
}
