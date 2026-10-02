package com.example.mijang.community.dto;

import java.time.LocalDateTime;

/** 마이페이지 "내 댓글" 목록의 한 줄을 담는다. */
public record MyCommentResponse(
        Long id,
        Long postId,
        String postTitle,
        String content,
        LocalDateTime createdAt,
        String status) {
}
