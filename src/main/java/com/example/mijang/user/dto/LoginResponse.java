package com.example.mijang.user.dto;

/**
 * 로그인 응답. 미장-API명세서 2장.
 *
 * <p>accessToken 은 쿠키로도 내려가지만 API 클라이언트를 위해 본문에도 담는다.
 * refreshToken 은 본문에 넣지 않는다. HttpOnly 쿠키로만 오간다.
 */
public record LoginResponse(String accessToken, LoginUserInfo user) {

    /**
     * 로그인 직후 화면이 바로 쓰는 최소 정보.
     *
     * <p>이메일은 넣지 않는다. 화면에 필요하면 {@code GET /api/users/me} 로 따로 받는다.
     * 로그인 응답은 여러 곳에 기록될 수 있어 담는 값을 줄이는 편이 낫다.
     */
    public record LoginUserInfo(Long id, String nickname, String role, String baseCurrency) {
    }
}
