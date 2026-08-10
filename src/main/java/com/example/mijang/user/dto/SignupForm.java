package com.example.mijang.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 회원가입 입력. 개발명세서(API) AUTH-001 */
@Getter
@Setter
public class SignupForm {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 64)
    private String password;

    @NotBlank
    @Size(max = 20)
    private String nickname;
}
