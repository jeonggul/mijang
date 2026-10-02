package com.example.mijang.common.dto;

import java.time.LocalDateTime;

/** 공지 목록과 상세가 함께 사용하는 읽기 모델. */
public record NoticeResponse(
        Long id,
        String title,
        String content,
        boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
