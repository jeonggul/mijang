package com.example.mijang.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 회원 탈퇴 확인 입력이다. */
@Getter
@Setter
public class AccountDeleteForm {

    /** 본인 확인용 비밀번호다 — 예전 규칙 값일 수 있어 형식 검사를 걸지 않는다. */
    @NotBlank
    private String password;
}
