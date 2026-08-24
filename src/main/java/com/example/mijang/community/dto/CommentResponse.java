/*
 * CommentResponse — 댓글 한 줄
 *
 * 이 파일이 하는 일
 *   게시글 상세에 딸려 나가는 댓글이다. 대댓글은 parentId 로 구분하고
 *   화면이 한 단 들여 그린다 — 깊이가 1단계뿐이라 트리로 접어 보낼 이유가 없다.
 */
package com.example.mijang.community.dto;

import java.time.LocalDateTime;

/**
 * 댓글. 개발명세서(API) COM-003·COM-004
 *
 * @param parentId 대댓글이면 부모 댓글 id. 원댓글은 null
 */
public record CommentResponse(
        Long id,
        Long parentId,
        String authorName,
        String content,
        LocalDateTime createdAt) {
}
