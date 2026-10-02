package com.example.mijang.user.dto;

/** 로그인 응답이다 — refreshToken은 본문에 넣지 않고 HttpOnly 쿠키로만 오간다. */
public record LoginResponse(String accessToken, LoginUserInfo user) {

    /** 로그인 직후 화면이 바로 쓰는 최소 사용자 정보다. */
    public record LoginUserInfo(Long id, String nickname, String role, String baseCurrency) {
    }
}
