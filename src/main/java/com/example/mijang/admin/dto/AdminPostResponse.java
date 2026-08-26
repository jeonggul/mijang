package com.example.mijang.admin.dto;

import java.time.LocalDateTime;

/** 관리자 게시글 목록 한 행. 검색 화면과 달리 상태가 그대로 보인다. */
public record AdminPostResponse(
        Long id,
        String board,
        String symbol,
        String title,
        String authorName,
        String status,
        long likeCount,
        long commentCount,
        long viewCount,
        LocalDateTime createdAt) {
}
