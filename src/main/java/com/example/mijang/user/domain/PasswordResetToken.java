package com.example.mijang.user.domain;

import java.time.LocalDateTime;

/** password_reset_tokens 한 행이다 — tokenHash는 메일로 나간 원문의 SHA-256이다. */
public record PasswordResetToken(
        Long tokenId,
        Long userId,
        String tokenHash,
        LocalDateTime expiresAt,
        LocalDateTime usedAt,
        LocalDateTime createdAt) {

    /** 아직 쓰지 않은 토큰인지 판정한다. */
    public boolean isUnused() {
        return usedAt == null;
    }

    /** 만료된 토큰인지 판정한다. */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt == null || expiresAt.isBefore(now);
    }
}
