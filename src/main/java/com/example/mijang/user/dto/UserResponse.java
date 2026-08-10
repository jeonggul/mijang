package com.example.mijang.user.dto;

import java.time.LocalDateTime;

/** 내 프로필 응답. 개발명세서(API) USER-001 */
public record UserResponse(Long userId, String email, String nickname, LocalDateTime joinedAt) {
}
