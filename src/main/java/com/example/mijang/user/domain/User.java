package com.example.mijang.user.domain;

import java.time.LocalDateTime;

/**
 * users 테이블 한 행.
 *
 * <p>passwordHash 는 항상 있다 — 스키마가 NOT NULL 이고, 소셜 가입도 비밀번호를
 * 함께 받는다. hasPassword() 는 그래도 남긴다. AuthService 가 "없는 이메일" 과
 * 한데 묶어 쓰고, 스키마를 되돌렸을 때 조용히 뚫리는 자리가 되지 않게 한다.
 */
public record User(
        Long id,
        String email,
        String passwordHash,
        int passwordVersion,
        String nickname,
        String profileImageUrl,
        String role,
        String baseCurrency,
        String theme,
        String status,
        LocalDateTime createdAt) {

    /**
     * 로그인시킬 수 있는 상태인지.
     *
     * <p>status 는 ACTIVE·SUSPENDED·WITHDRAWN 셋이다. 정지 계정을 여기서 걸러
     * 로그인 흐름 한 군데에서만 판단하게 한다.
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
     * 비밀번호 로그인이 가능한 계정인지.
     *
     * <p>지금은 스키마가 NOT NULL 이라 항상 참이다. 그래도 남기는 이유 —
     * {@code AuthService} 가 "없는 이메일" 과 이 경우를 한데 묶어 타이밍에 안전한
     * 하나의 실패 경로로 쓰고, 스키마를 되돌렸을 때 이 검사가 없으면 BCrypt
     * matches 에 null 이 들어가 NPE 가 나거나 아무도 모르게 뚫리는 자리가 된다.
     */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
