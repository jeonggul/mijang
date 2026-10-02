package com.example.mijang.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 회원 탈퇴 확인. 개발명세서(API) AUTH-06 */
@Getter
@Setter
public class AccountDeleteForm {

    /** 본인 확인용. 형식 검사를 걸지 않는다 — 예전 규칙으로 만든 값일 수 있다. */
    @NotBlank
    private String password;
}
