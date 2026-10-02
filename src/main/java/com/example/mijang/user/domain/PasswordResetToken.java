package com.example.mijang.user.domain;

import java.time.LocalDateTime;

/**
 * password_reset_tokens 한 행.
 *
 * <p>메일로 나간 원문은 여기 없다. {@code tokenHash} 는 원문의 SHA-256 이고,
 * 검증할 때도 들어온 값을 같은 방식으로 해시해 비교한다.
 */
public record PasswordResetToken(
        Long tokenId,
        Long userId,
        String tokenHash,
        LocalDateTime expiresAt,
        LocalDateTime usedAt,
        LocalDateTime createdAt) {

    /** 아직 쓰지 않았는가. */
    public boolean isUnused() {
        return usedAt == null;
    }

    /** 만료됐는가. */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt == null || expiresAt.isBefore(now);
    }
}
