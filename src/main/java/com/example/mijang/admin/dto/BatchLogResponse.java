package com.example.mijang.admin.dto;

import java.time.LocalDateTime;

/** 배치 실행 기록 한 건 — 시작·소요·처리 건수·종료 상태를 담는다. */
public record BatchLogResponse(
        String jobName,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer durationMs,
        Integer processedCount,
        String status,
        String message) {
}
