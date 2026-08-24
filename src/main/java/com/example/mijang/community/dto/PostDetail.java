/*
 * PostDetail — 게시글 상세
 *
 * 이 파일이 하는 일
 *   상세 화면이 한 번에 그리는 값이다. 댓글까지 같이 담는다 —
 *   글을 열면 댓글은 반드시 보이므로 나눠 부르면 요청만 두 번이 된다.
 */
package com.example.mijang.community.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 상세와 댓글. 개발명세서(API) COM-003
 *
 * @param mine 지금 보는 사람이 쓴 글인지. 수정·삭제 버튼을 띄울지 화면이 이걸로 정한다.
 *             화면이 작성자 이름을 비교하게 두면 동명이인에서 틀린다
 */
public record PostDetail(
        Long id,
        String board,
        String symbol,
        String title,
        String content,
        String authorName,
        boolean shareholder,
        BigDecimal priceAtWrite,
        TradeCard trade,
        long likeCount,
        long commentCount,
        long viewCount,
        LocalDateTime createdAt,
        boolean mine,
        List<CommentResponse> comments) {
}
