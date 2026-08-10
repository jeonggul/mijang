package com.example.mijang.security;

/**
 * 로그인 사용자 식별 정보. 세션(또는 토큰)에서 꺼내 컨트롤러로 넘긴다.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.security
 */
public record SessionUser(Long userId, String email, String nickname) {

    public static final String SESSION_KEY = "MIJANG_SESSION_USER";
}
