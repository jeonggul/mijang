package com.example.mijang.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 로그인 입력이다. */
@Getter
@Setter
public class LoginForm {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    /** 로그인 유지 여부다 — 지금은 받기만 하고 쓰지 않는다. */
    private boolean rememberMe;
}
