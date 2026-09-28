package com.example.mijang.community.dto;

import java.time.LocalDateTime;

/** 게시글 상세에 딸려 나가는 댓글 한 줄을 담는다. 대댓글은 parentId 로 구분한다. */
public record CommentResponse(
        Long id,
        Long parentId,
        String authorName,
        String content,
        LocalDateTime createdAt) {
}
