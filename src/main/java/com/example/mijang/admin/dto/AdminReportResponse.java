package com.example.mijang.admin.dto;

import java.time.LocalDateTime;

/** 관리자 신고 목록 한 행. targetSummary 는 글이면 제목, 댓글이면 앞부분이다. */
public record AdminReportResponse(
        Long id,
        String targetType,
        Long targetId,
        String targetSummary,
        String reason,
        String detail,
        String status,
        String reporterName,
        LocalDateTime createdAt) {
}
