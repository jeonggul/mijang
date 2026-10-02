package com.example.mijang.user.dto;

import java.time.LocalDateTime;

/** 마이페이지가 받아 가는 내 프로필 응답이다 — 비밀번호 해시는 담지 않는다. */
public record UserResponse(
        Long userId,
        String email,
        String nickname,
        String profileImageUrl,
        String role,
        String baseCurrency,
        String theme,
        LocalDateTime joinedAt,
        LocalDateTime passwordChangedAt) {
}
