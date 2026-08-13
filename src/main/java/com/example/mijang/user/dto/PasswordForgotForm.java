package com.example.mijang.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 재설정 링크 요청. 개발명세서(API) AUTH-05 */
@Getter
@Setter
public class PasswordForgotForm {

    @NotBlank
    @Email
    private String email;
}
