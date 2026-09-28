package com.example.mijang.community.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 게시글 목록 한 줄을 담는다. 본문 대신 앞머리(excerpt)만 싣는다. */
public record PostSummary(
        Long id,
        String board,
        String symbol,
        String title,
        String excerpt,
        String authorName,
        boolean shareholder,
        BigDecimal priceAtWrite,
        TradeCard trade,
        long likeCount,
        long commentCount,
        LocalDateTime createdAt,
        /* 내 글 목록에서만 쓴다 */
        String status) {
}
