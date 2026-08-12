package com.example.mijang.security;

/**
 * 로그인 사용자 식별 정보. access token 의 클레임에서 만들어져 컨트롤러까지 간다.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.security
 * <p>이메일은 담지 않는다. 바뀔 수 있는 값을 토큰에 넣으면 만료 전까지 옛 값이 돌아다닌다.
 * 필요하면 {@code userId} 로 조회한다.
 */
public record SessionUser(Long userId, String nickname, String role) {

    /**
     * 관리자인지. 화면 템플릿에서 관리자 메뉴를 노출할지 판단할 때 쓴다.
     *
     * <p>API·화면 접근 통제는 이 메서드가 아니라 SecurityConfig 가 한다.
     * 여기에 의존해 막으면 컨트롤러마다 빠뜨릴 수 있다.
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
