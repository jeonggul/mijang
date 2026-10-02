package com.example.mijang.admin.dto;

import java.time.LocalDateTime;

/** 운영 로그 한 건. targetLabel 은 작업 당시의 표시용 스냅샷이라 원본이 사라져도 남는다. */
public record AdminLogResponse(
        Long id,
        String adminNickname,
        String action,
        String targetType,
        String targetId,
        String targetLabel,
        String detail,
        String result,
        LocalDateTime createdAt) {
}
