package com.example.mijang.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 로그인 입력. 개발명세서(API) AUTH-002 */
@Getter
@Setter
public class LoginForm {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
