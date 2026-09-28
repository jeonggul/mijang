package com.example.mijang.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 비밀번호 재설정 링크 요청 입력이다. */
@Getter
@Setter
public class PasswordForgotForm {

    @NotBlank
    @Email
    private String email;
}
