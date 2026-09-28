package com.example.mijang.security;

/** access token 클레임에서 만든 로그인 사용자 식별 정보를 담는다. */
public record SessionUser(Long userId, String nickname, String role) {

    /** 관리자인지 확인한다. 접근 통제가 아닌 화면 노출 판단에만 쓴다. */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
