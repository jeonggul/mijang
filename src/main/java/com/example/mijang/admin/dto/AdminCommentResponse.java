package com.example.mijang.admin.dto;

import java.time.LocalDateTime;

/** 관리자 댓글 목록 한 행. 어느 글에 달렸는지 함께 준다. */
public record AdminCommentResponse(
        Long id,
        Long postId,
        String postTitle,
        String authorName,
        String content,
        String status,
        LocalDateTime createdAt) {
}
