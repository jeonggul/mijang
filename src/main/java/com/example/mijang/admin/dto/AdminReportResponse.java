package com.example.mijang.admin.dto;

import java.time.LocalDateTime;

/**
 * 관리자 신고 목록 한 행.
 *
 * <p>{@code targetSummary} 는 대상이 글이면 제목, 댓글이면 앞부분이다 —
 * 신고만 보고도 무엇이 신고됐는지 알 수 있어야 목록에서 눌러 볼 것을 고른다.
 */
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
