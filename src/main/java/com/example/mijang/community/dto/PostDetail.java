package com.example.mijang.community.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 게시글 상세와 댓글을 한 번에 담는다. */
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
        /* PUBLISHED 가 아니면 쓴 사람에게만 보이는 글이다 */
        String status,
        boolean mine,
        boolean myLike,
        boolean myScrap,
        List<CommentResponse> comments) {
}
