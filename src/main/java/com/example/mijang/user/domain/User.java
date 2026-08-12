package com.example.mijang.user.domain;

import java.time.LocalDateTime;

/**
 * users 테이블 한 행.
 *
 * <p>소셜 전용 계정은 passwordHash 가 null 이다 (스키마 주석).
 */
public record User(
        Long id,
        String email,
        String passwordHash,
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
     * <p>소셜 전용 계정은 password_hash 가 null 이다(스키마 주석).
     * 이 검사를 빠뜨리면 BCrypt matches 에 null 이 들어가 NPE 가 난다.
     */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
