package com.example.mijang.user.domain;

import java.time.LocalDateTime;

/** users 테이블 한 행이다. */
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

    /** 로그인시킬 수 있는 ACTIVE 상태인지 판정한다. */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /** 비밀번호 로그인이 가능한 계정인지 판정한다 — 스키마상 항상 참이지만 null 해시 방어용으로 남긴다. */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
