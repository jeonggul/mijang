package com.example.mijang.admin.domain;

/** 관리자 상태 변경 판단에 필요한 사용자 원본 값. */
public record AdminUserAccount(
        Long id,
        String email,
        String nickname,
        String role,
        String status,
        int passwordVersion) {
}
