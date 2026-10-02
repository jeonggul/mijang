package com.example.mijang.admin.dto;

import java.time.LocalDateTime;

/** 관리자 사용자 목록의 한 행. 비밀번호 같은 인증 정보는 내보내지 않는다. */
public record AdminUserResponse(
        Long userId,
        String nickname,
        String email,
        LocalDateTime createdAt,
        int transactionCount,
        int postCount,
        String role,
        String status,
        boolean manageable) {
}
