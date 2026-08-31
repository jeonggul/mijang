/*
 * MyCommentResponse — 내가 쓴 댓글 한 줄
 *
 * 이 파일이 하는 일
 *   마이페이지의 "내 댓글" 목록에 나가는 값이다.
 *
 *   왜 CommentResponse 를 쓰지 않는가
 *     그쪽은 글 상세 안에서 쓰는 값이라 어느 글에 달렸는지가 없다. 이미 글을 보고
 *     있으니 필요가 없다. 내 댓글 목록은 반대다 — 글이 정해져 있지 않아서
 *     제목과 글 번호가 없으면 어디에 쓴 댓글인지 알 수 없고, 눌러 갈 수도 없다.
 *
 *   작성자 이름은 담지 않는다. 전부 내 것이라 같은 이름이 줄줄이 반복될 뿐이다.
 */
package com.example.mijang.community.dto;

import java.time.LocalDateTime;

/**
 * 내가 쓴 댓글. {@code MY-03}
 *
 * @param postTitle 달린 글의 제목. 글이 지워졌으면 null 이다
 * @param status    {@code PUBLISHED} · {@code HIDDEN} · {@code DELETED}.
 *                  남에게 안 보이는 댓글도 쓴 사람에게는 보여야 왜 사라졌는지 안다
 */
public record MyCommentResponse(
        Long id,
        Long postId,
        String postTitle,
        String content,
        LocalDateTime createdAt,
        String status) {
}
